package com.plumejade.lensouls.client;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：「这名玩家看起来正拿着这台相机」的显示态缓存（纯渲染用，不改任何物品）。
 * <p>
 * 服务端在玩家用饰品栏相机开/关取景时广播一份；本类只被 mixin 读，用来在渲染把手持物
 * 换成相机。实体 id 由服务端给，登出/换维度时服务端会补一条空包，卸载世界时整体清空。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public final class VirtualHandCache {

    private static final Map<Integer, ItemStack> STACKS = new HashMap<>();

    private VirtualHandCache() {
    }

    public static void set(int entityId, ItemStack stack) {
        if (stack.isEmpty()) {
            STACKS.remove(entityId);
        } else {
            STACKS.put(entityId, stack);
        }
    }

    /**
     * 该玩家当前应「看起来拿在手上」的物品；没有就是空栈。
     * <p>
     * ⚠ 这里**绝对不能**去问 {@code getActiveExposureCamera()}：本方法是从
     * {@code Player#getItemBySlot} 的 mixin 里调进来的，而 Exposure 的
     * {@code getActiveExposureCamera()} 内部又会读 {@code getItemInHand(...)} →
     * {@code getItemBySlot}，直接递归到栈溢出（1.8.180 实测崩客户端）。
     * 本地玩家的实时性由 {@link #onClientTick} 在 tick 阶段推进缓存来保证。
     */
    public static ItemStack forPlayer(Player player) {
        if (STACKS.isEmpty()) return ItemStack.EMPTY;
        return STACKS.getOrDefault(player.getId(), ItemStack.EMPTY);
    }

    /**
     * 本地玩家的显示态每 tick 从「客户端 Curios 里那根活跃相机栈」刷新一次。
     * <p>
     * 切自拍（第三人称前视角）时，Exposure 的 {@code CameraSetting.setAndSync} 是先在本地写栈、
     * 再发 C2S；服务端那份要等下一次广播才回到客户端，只靠广播的话模型会慢 2~3 tick
     * （用户实测「旧姿势慢几 tick 才刷新」）。tick 阶段读实时栈没有递归风险。
     */
    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        try {
            var viewfinder = io.github.mortuusars.exposure.client.camera.CameraClient.viewfinder();
            if (viewfinder == null) return;
            if (!(viewfinder.camera() instanceof com.plumejade.lensouls.ability.camera.CameraInCurio curio)) return;
            ItemStack live = curio.getItemStack();
            if (live.isEmpty()) return;
            STACKS.put(player.getId(), live.copy());
        } catch (Throwable ignored) {
            // 取景器尚未建立/已销毁：保持服务端广播的那份即可
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) STACKS.clear();
    }
}