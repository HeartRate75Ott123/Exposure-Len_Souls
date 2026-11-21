package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityBehavior;
import com.plumejade.lensouls.ability.CameraStacks;
import com.plumejade.lensouls.ability.gui.AbilityGuiHolder;
import com.plumejade.lensouls.ability.network.AbilityOpenGuiPacket;
import com.plumejade.lensouls.ability.network.SpatialWarpActivatePacket;
import com.plumejade.lensouls.ability.network.TemporalRecallTriggerPacket;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端：检测手持相机左键空气，根据当前能力发送对应的 C2S 包。
 * <p>
 * ⚠ 本类属于**仅客户端**（引用 LocalPlayer/Minecraft/KeyMapping）。服务端要查相机一律用
 * {@link CameraStacks}——服务端路径引用本类里的方法会在专用服务器上抛 dist 异常。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class CameraInputHandler {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        ItemStack stack = mc.player.getMainHandItem();

        // ── 主手持有空间扭曲照片 → 开/关空间扭曲 ──
        // 口径（用户定）：手持 + 左键就认，不再要求「左键点到空气」——
        // 对着方块/生物左键同样生效；这里不取消原版左键，破坏/攻击照常进行。
        if (hasSpatialWarpData(stack)) {
            PacketDistributor.sendToServer(new SpatialWarpActivatePacket());
            return;
        }

        // 下面两条仍然只认「左键点空」，避免与破坏方块/攻击抢输入
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return;

        // ── 主手持有回溯快照照片 → 主动触发回溯 ──
        if (TemporalSnapshot.hasSnapshot(stack)) {
            PacketDistributor.sendToServer(new TemporalRecallTriggerPacket());
            return;
        }

        // ── 手持相机 → 打开能力选择 GUI ──
        if (CameraStacks.isCamera(stack)) {
            PacketDistributor.sendToServer(new AbilityOpenGuiPacket());
        }
    }

    /**
     * 能力选择 GUI 打开时仍可移动（WASD/跳跃/潜行/冲刺）。
     * <p>
     * 原版 Screen 打开时 KeyMapping 不更新，input.tick 读到全 false；
     * 该事件在 {@code input.tick} 之后、移动逻辑之前触发，
     * 用 GLFW 原始键位覆写 Input 字段即可正常移动（仅限本模组能力 GUI）。
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null) return;
        if (!(mc.screen instanceof ModularUIContainerScreen cs)) return;
        if (!(cs.getMenu() instanceof ModularUIContainerMenu menu)) return;
        if (!AbilityGuiHolder.isGuiOpen(menu.getModularUI())) return;

        Input input = event.getInput();
        boolean up = keyDown(mc, mc.options.keyUp);
        boolean down = keyDown(mc, mc.options.keyDown);
        boolean left = keyDown(mc, mc.options.keyLeft);
        boolean right = keyDown(mc, mc.options.keyRight);
        input.up = up;
        input.down = down;
        input.left = left;
        input.right = right;
        input.forwardImpulse = impulse(up, down);
        input.leftImpulse = impulse(left, right);
        input.jumping = keyDown(mc, mc.options.keyJump);
        input.shiftKeyDown = keyDown(mc, mc.options.keyShift);
        // 冲刺由 aiStep 读 keySprint.isDown() 触发，直接同步原始键位
        mc.options.keySprint.setDown(keyDown(mc, mc.options.keySprint));
    }

    private static float impulse(boolean input, boolean otherInput) {
        return input == otherInput ? 0.0F : (input ? 1.0F : -1.0F);
    }

    private static boolean keyDown(Minecraft mc, KeyMapping km) {
        var key = km.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
    }

    /** 客户端用：按当前手持/副手的相机类型过滤能力列表（轮盘/快捷列表/菜单共用）。 */
    public static java.util.List<com.plumejade.lensouls.ability.AbilityType> filterHeld(
            java.util.List<com.plumejade.lensouls.ability.AbilityType> list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return list;
        return com.plumejade.lensouls.ability.CameraAbilityRules.filterFor(list, CameraStacks.getWieldedCamera(mc.player));
    }

    private static boolean hasSpatialWarpData(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.contains(AbilityBehavior.TAG_WARP_POS);
    }
}
