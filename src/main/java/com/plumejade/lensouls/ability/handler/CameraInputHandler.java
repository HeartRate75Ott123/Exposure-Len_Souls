package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.network.AbilityCyclePacket;
import com.plumejade.lensouls.ability.network.SpatialWarpActivatePacket;
import com.plumejade.lensouls.ability.network.TemporalRecallTriggerPacket;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
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

        // ── 手持相机 → 循环切换能力（首次切换自动发送弱点透镜描述） ──
        if (isCamera(stack)) {
            PacketDistributor.sendToServer(new AbilityCyclePacket());
        }
    }

    // ========== 相机检测（按 ID 比对，无编译依赖） ==========

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");

    private static boolean isCamera(ItemStack stack) {
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
