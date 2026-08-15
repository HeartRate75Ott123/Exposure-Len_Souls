package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class CameraInputHandler {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return;

        ItemStack stack = mc.player.getMainHandItem();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        // ── 主手持有空间扭曲照片 → 激活空间扭曲 ──
        if (hasSpatialWarpData(stack)) {
            PacketDistributor.sendToServer(new SpatialWarpActivatePacket());
            return;
        }

        // ── 主手持有回溯快照照片 → 主动触发回溯 ──
        if (TemporalSnapshot.hasSnapshot(stack)) {
            PacketDistributor.sendToServer(new TemporalRecallTriggerPacket());
            return;
        }

        // ── 手持相机 → 打开能力选择 GUI ──
        if (isCamera(stack)) {
            PacketDistributor.sendToServer(new AbilityOpenGuiPacket());
        }
    }

    // ========== 相机检测（按 ID 比对，无编译依赖） ==========

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");

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

    /** 相机检测（按 ID 比对，无编译依赖）；HUD 滚动列表等客户端组件复用 */
    public static boolean isCamera(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }

    private static boolean hasSpatialWarpData(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.contains("lensouls:spatial_warp_pos");
    }
}
