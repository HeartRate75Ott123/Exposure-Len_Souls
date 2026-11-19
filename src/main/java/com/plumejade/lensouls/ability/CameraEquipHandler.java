package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.camera.CameraInCurio;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import io.github.mortuusars.exposure.world.camera.Camera;
import io.github.mortuusars.exposure.world.camera.CameraId;
import io.github.mortuusars.exposure.world.entity.CameraOperator;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 饰品栏相机：按 Z 直接打开取景框（相机留在饰品槽里，不弄到手上）。
 * <p>
 * 靠 {@link CameraInCurio} 把「活跃相机」指向饰品槽里的那台：Exposure 的取景框、快门、
 * 胶卷消耗全部走 {@code Camera.getStack()}，所以消耗天然落在同一台相机上，物品也不用来回搬。
 */
@EventBusSubscriber(modid = LenSouls.MODID)
public final class CameraEquipHandler {

    /** 每个玩家上一次同步出去的相机栈快照（只在「饰品栏相机处于取景态」时才用）。 */
    private static final Map<UUID, ItemStack> LAST_SYNCED = new HashMap<>();

    private CameraEquipHandler() {
    }

    /** 一件装备着的相机：槽位 id + 下标 + 栈（客户端从 Curios 同步数据读）。 */
    public record Entry(String slotId, int index, ItemStack stack) {
    }

    /** 收集身上所有饰品槽里的相机（主手/副手不在此列）。 */
    public static List<Entry> collect(Player player) {
        List<Entry> out = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return out;
        for (Map.Entry<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler> e
                : handler.getCurios().entrySet()) {
            var stacks = e.getValue().getStacks();
            int slots = Math.min(stacks.getSlots(), e.getValue().getSlots());
            for (int i = 0; i < slots; i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.isEmpty() || !CameraInputHandler.isCamera(stack)) continue;
                out.add(new Entry(e.getKey(), i, stack));
            }
        }
        return out;
    }

    public static ItemStack stackAt(Player player, String slotId, int index) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return ItemStack.EMPTY;
        var stacksHandler = handler.getStacksHandler(slotId).orElse(null);
        if (stacksHandler == null) return ItemStack.EMPTY;
        var stacks = stacksHandler.getStacks();
        if (index < 0 || index >= stacks.getSlots()) return ItemStack.EMPTY;
        return stacks.getStackInSlot(index);
    }

    /** 服务端权威：同一台再按一次 = 关闭；按另一台 = 换那台。 */
    public static void toggleFromCurio(ServerPlayer player, String slotId, int index) {
        CameraOperator operator = (CameraOperator) player;
        Camera active = operator.getActiveExposureCamera();
        boolean sameTarget = active instanceof CameraInCurio curio
                && curio.slotId().equals(slotId) && curio.index() == index;
        String activeSlot = active instanceof CameraInCurio c0 ? c0.slotId() : null;
        int activeIndex = active instanceof CameraInCurio c1 ? c1.index() : -1;
        if (active != null) {
            active.deactivate();
            operator.removeActiveExposureCamera();
            if (activeSlot != null) syncSlot(player, activeSlot, activeIndex);
            if (sameTarget) return;
        }

        ItemStack stack = stackAt(player, slotId, index);
        if (stack.isEmpty() || !(stack.getItem() instanceof CameraItem item)) return;

        CameraId id = item.getOrCreateId(stack);
        operator.setActiveExposureCamera(new CameraInCurio((io.github.mortuusars.exposure.world.entity.CameraHolder) player, id, slotId, index));
        item.activate(player, stack);
        // 直接改 slot 里的 ItemStack 不会触发 Curios 的变更同步，客户端那份就看不到
        // CAMERA_ACTIVE / 胶卷数变化 —— 写回一次，强制同步。
        syncSlot(player, slotId, index);
        LenSouls.LOGGER.debug("[Camera] 饰品栏相机开取景 slot={} index={}", slotId, index);
    }

    /**
     * 取景期间把饰品槽里的相机栈「当作拿在手上」同步给客户端。
     * <p>
     * Exposure 拍摄时会就地改这根栈（快门状态、相机设置、相纸/胶片消耗、冷却相关的前置态），
     * 手持物品走的是原版背包同步；饰品槽里的栈就地改**不会**自动同步，
     * 客户端那份就会停在旧值（相纸数不动、取景框状态对不上）。
     * 这里每个 tick 比一次快照，变了就 {@code setEquippedCurio} 写回一句，
     * 于是冷却、快门、相纸数都和手持一致。
     */
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            // ---- 让饰品槽里的相机像拿在手上一样逐 tick 跑 ----
            // 原版相机的快门/定时器/投影全部挂在 Item#inventoryTick → CameraItem.tick 上，
            // 而饰品槽里的物品**不会**收到 inventoryTick。缺了这一下：
            //   快门永远关不上 ⇒ 不出片、不进冷却（用户实测「两种相机都拍不了」就是这个）。
            for (Entry entry : collect(player)) {
                if (entry.stack().getItem() instanceof CameraItem cameraItem) {
                    cameraItem.tick((io.github.mortuusars.exposure.world.entity.CameraHolder) player,
                            entry.stack());
                }
            }

            Camera active = ((CameraOperator) player).getActiveExposureCamera();
            if (!(active instanceof CameraInCurio curio)) {
                LAST_SYNCED.remove(player.getUUID());
                continue;
            }
            ItemStack current = curio.getItemStack();
            if (current.isEmpty()) continue;
            ItemStack previous = LAST_SYNCED.put(player.getUUID(), current.copy());
            if (previous != null && ItemStack.matches(previous, current)) continue;
            CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.setEquippedCurio(curio.slotId(), curio.index(), current.copy()));
        }
    }

    /** 把槽里那份 ItemStack 原样写回，触发 Curios 的变更同步（服务端→客户端）。 */
    private static void syncSlot(Player player, String slotId, int index) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).orElse(null);
        if (handler == null) return;
        ItemStack current = stackAt(player, slotId, index);
        if (current.isEmpty()) return;
        handler.setEquippedCurio(slotId, index, current.copy());
    }

    /** 客户端提示用（身上没相机时）。 */
    public static Component noneMessage() {
        return Component.translatable("camera.lensouls.none").withStyle(ChatFormatting.RED);
    }
}
