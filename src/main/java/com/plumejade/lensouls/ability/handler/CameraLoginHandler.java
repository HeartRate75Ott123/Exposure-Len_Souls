package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 相机状态清理处理器。
 * <p>
 * 玩家重进世界时，清除所有相机物品的 {@code camera_active} 状态，
 * 避免 {@link io.github.mortuusars.exposure.world.item.camera.CameraItem#use}
 * 因持久化的 active 状态而直接进入 release() 流程。
 * <p>
 * Exposure 自身仅在客户端 {@code inventoryTick} 中清理服务端状态，
 * 重进世界时服务端从存档恢复的 {@code camera_active=true} 不受影响。
 */
public class CameraLoginHandler {

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");
    private static final ResourceLocation CAMERA_ACTIVE_KEY = ResourceLocation.parse("exposure:camera_active");

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Inventory inv = event.getEntity().getInventory();

        // 查找 CameraActive DataComponentType
        Registry<DataComponentType<?>> registry = BuiltInRegistries.DATA_COMPONENT_TYPE;
        DataComponentType<?> activeType = registry.get(CAMERA_ACTIVE_KEY);
        if (activeType == null) return; // Exposure 未加载

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (isCamera(stack) && stack.has(activeType)) {
                stack.remove(activeType);
            }
        }
    }

    private static boolean isCamera(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }
}
