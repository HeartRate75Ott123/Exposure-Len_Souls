package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
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
 * 拍立得 {@code printPhotograph} 出片时，从队列取能力并注入照片数据。
 */
@Mixin(targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem", remap = false)
public class PolaroidPrintMixin {

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private void lensouls$markAfterPrint(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        if (!(holder.asHolderEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        AbilityType ability = PhotoInjectionHandler.pollAbility(player.getUUID());
        if (ability == null) return;

        ExposureIdentifier targetId = frame.identifier();
        if (targetId == null || targetId.isEmpty()) return;

        findAndMarkInInventory(player, targetId, ability, frame);
    }

    // ========== 背包扫描 ==========

    /** 用 ExposureIdentifier 精确匹配玩家背包中的照片 */
    private static boolean findAndMarkInInventory(Player player, ExposureIdentifier targetId, AbilityType ability, Frame frame) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) {
                Frame stackFrame = stack.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
                if (stackFrame != null && targetId.equals(stackFrame.identifier())) {
                    inject(stack, ability, player, frame);
                    return true;
                }
            }

            if (stack.getItem() instanceof StackedPhotographsItem spItem) {
                StackedPhotographs photos = spItem.getPhotographs(stack);
                boolean changed = false;
                for (int j = 0; j < photos.size(); j++) {
                    ItemStack inner = photos.getItemUnsafe(j);
                    Frame innerFrame = inner.get(Exposure.DataComponents.PHOTOGRAPH_FRAME);
                    if (innerFrame != null && targetId.equals(innerFrame.identifier())) {
                        inject(inner, ability, player, frame);
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

    /** 往照片 ItemStack 写入能力标记、额外数据、改名 */
    private static void inject(ItemStack photo, AbilityType ability, Player player, Frame frame) {
        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("lensouls:injected")) return;

        tag.putBoolean("lensouls:injected", true);
        tag.putString("lensouls:ability_type", ability.getId());

        String nameKey = "ability.lensouls." + ability.getId() + ".name";

        switch (ability) {
            case SPATIAL_WARP -> {
                CompoundTag posTag = new CompoundTag();
                posTag.putDouble("x", player.getX());
                posTag.putDouble("y", player.getY());
                posTag.putDouble("z", player.getZ());
                tag.put("lensouls:spatial_warp_pos", posTag);
            }
            case TEMPORAL_RECALL -> {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    TemporalSnapshot snapshot = TemporalSnapshot.capture(sp);
                    tag.put("lensouls:snapshot", snapshot.toTag());
                }
            }
            case ABILITY_STEAL -> {
                String entityId = PhotoInjectionHandler.pollStolenEntity(
                        frame.identifier() != null ? frame.identifier().toString() : null);
                if (entityId != null && !entityId.isEmpty()) {
                    tag.putBoolean("lensouls:ability_steal", true);
                    tag.putString("lensouls:stolen_entity", entityId);
                    if (PhotographEffectRegistry.hasEffect(entityId)) {
                        tag.putBoolean("lensouls:photograph_curio", true);
                    }
                }
            }
        }

        // 所有能力统一绿色改名，标识为能力照片
        MutableComponent name = Component.literal("")
                .append(Component.translatable(nameKey))
                .append(Component.literal("照片"))
                .withStyle(ChatFormatting.GREEN)
                .withStyle(s -> s.withItalic(false).withBold(false));
        photo.set(DataComponents.CUSTOM_NAME, name);

        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
