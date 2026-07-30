package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.component.StackedPhotographs;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拍立得 {@code printPhotograph} 出片时，用 Frame 的 {@code ExposureIdentifier}
 * 精确匹配刚拍的照片，注入能力数据和改名。
 * <p>
 * 相比旧的"扫背包第一个未标记"方案：
 * <ul>
 *   <li>用帧标识符定位，不担心旧照片混淆</li>
 *   <li>同时处理单张和堆叠照片</li>
 *   <li>空间扭曲/时空回溯照片额外写入对应数据</li>
 *   <li>注入后清空相机 {@code capture_ability}，防泄漏</li>
 *   <li>不碰 {@code pendingQueue}（队列仅限 Lightroom 路径使用）</li>
 * </ul>
 */
@Mixin(targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem", remap = false)
public class PolaroidPrintMixin {

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private void lensouls$markAfterPrint(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        Entity entity = holder.asHolderEntity();
        if (!(entity instanceof Player player)) return;

        CompoundTag cameraTag = cameraStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String abilityId = cameraTag.getString("lensouls:capture_ability");
        if (abilityId.isEmpty()) return;

        ExposureIdentifier targetId = frame.identifier();
        if (targetId == null || targetId.isEmpty()) return;

        if (findAndMarkInInventory(player, targetId, abilityId, cameraTag)) {
            cleanupCamera(cameraStack, cameraTag);
        }
    }

    // ========== 背包扫描 ==========

    /** 用 ExposureIdentifier 精确匹配玩家背包中的照片 */
    private static boolean findAndMarkInInventory(Player player, ExposureIdentifier targetId, String abilityId, CompoundTag cameraTag) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 单张照片：匹配 Frame 标识符
            if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) {
                Frame stackFrame = stack.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
                if (stackFrame != null && targetId.equals(stackFrame.identifier())) {
                    inject(stack, abilityId, player, cameraTag);
                    return true;
                }
            }

            // 堆叠照片：遍历内层照片匹配
            if (stack.getItem() instanceof StackedPhotographsItem spItem) {
                StackedPhotographs photos = spItem.getPhotographs(stack);
                boolean changed = false;
                for (int j = 0; j < photos.size(); j++) {
                    ItemStack inner = photos.getItemUnsafe(j);
                    Frame innerFrame = inner.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
                    if (innerFrame != null && targetId.equals(innerFrame.identifier())) {
                        inject(inner, abilityId, player, cameraTag);
                        changed = true;
                        break;
                    }
                }
                if (changed) {
                    spItem.setPhotographs(stack, photos);
                    return true;
                }
            }
        }
        return false;
    }

    // ========== 注入与清理 ==========

    /** 往照片 ItemStack 写入能力标记、额外数据、改名、强制单张 */
    private static void inject(ItemStack photo, String abilityId, Player player, CompoundTag cameraTag) {
        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("lensouls:injected")) return;

        tag.putBoolean("lensouls:injected", true);
        tag.putString("lensouls:ability_type", abilityId);

        // 能力特定数据
        switch (abilityId) {
            case "spatial_warp" -> {
                CompoundTag posTag = new CompoundTag();
                posTag.putDouble("x", player.getX());
                posTag.putDouble("y", player.getY());
                posTag.putDouble("z", player.getZ());
                tag.put("lensouls:spatial_warp_pos", posTag);
            }
            case "temporal_recall" -> {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    TemporalSnapshot snapshot = TemporalSnapshot.capture(sp);
                    tag.put("lensouls:snapshot", snapshot.toTag());
                }
            }
            case "ability_steal" -> {
                String entityId = cameraTag.getString("lensouls:stolen_entity");
                if (!entityId.isEmpty()) {
                    tag.putBoolean("lensouls:ability_steal", true);
                    tag.putString("lensouls:stolen_entity", entityId);
                }
            }
        }

        // 所有能力通用：注入被窃取实体 ID（照片饰品系统使用）
        if (!tag.contains("lensouls:stolen_entity")) {
            String entityId = cameraTag.getString("lensouls:stolen_entity");
            if (!entityId.isEmpty()) {
                tag.putString("lensouls:stolen_entity", entityId);
            }
        }

        // 有注册效果的实体 → 标记为照片饰品 + 绿色名；否则保持普通照片
        String entityId = tag.getString("lensouls:stolen_entity");
        if (!entityId.isEmpty() && PhotographEffectRegistry.hasEffect(entityId)) {
            tag.putBoolean("lensouls:photograph_curio", true);
            String transKey = "ability.lensouls." + abilityId + ".name";
            MutableComponent name = Component.literal("")
                    .append(Component.literal("照片("))
                    .append(Component.translatable(transKey))
                    .append(Component.literal(")"))
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(s -> s.withItalic(false).withBold(false));
            photo.set(DataComponents.CUSTOM_NAME, name);
        }

        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void cleanupCamera(ItemStack cameraStack, CompoundTag cameraTag) {
        CompoundTag clean = cameraTag.copy();
        clean.remove("lensouls:capture_ability");
        clean.remove("lensouls:stolen_entity");
        cameraStack.set(DataComponents.CUSTOM_DATA,
                clean.isEmpty() ? CustomData.EMPTY : CustomData.of(clean));
    }
}
