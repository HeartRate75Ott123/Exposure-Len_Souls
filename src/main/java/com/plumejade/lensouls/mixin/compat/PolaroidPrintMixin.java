package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拍立得 {@code printPhotograph} 出片时注入能力数据。
 * <p>
 * 拦截照片进入背包前的关键调用点：
 * <ul>
 *   <li>{@code Inventory.setItem} — 空格直接塞入</li>
 *   <li>{@code StackedPhotographsItem.addPhotographOnTop} / {@code StackedPhotographs.addPhotographOnTop} — 堆叠</li>
 * </ul>
 * 通过 ThreadLocal 暂存照片引用，RETURN 时从队列取能力并注入。
 */
@Mixin(targets = "io.github.mortuusars.exposure_polaroid.world.item.InstantCameraItem", remap = false)
public class PolaroidPrintMixin {

    @Unique
    private static final ThreadLocal<ItemStack> capturedPhoto = new ThreadLocal<>();

    // ── 空空插槽路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setItem(ILnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack lensouls$captureSetItem(ItemStack stack) {
        LenSouls.LOGGER.info("[Polaroid] capture: setItem");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    // ── 堆叠路径（首次和已有都走 StackedPhotographsItem.addPhotographOnTop） ──
    // 注意：不需要 ordinal，因为只有最后一个 addPhotographOnTop 会写入新照片
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lio/github/mortuusars/exposure/world/item/StackedPhotographsItem;addPhotographOnTop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V"), index = 1, require = 0)
    private static ItemStack lensouls$captureStack(ItemStack stack) {
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    // ── 背包满时直接丢地上路径 ──
    @ModifyArg(method = "printPhotograph", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"), index = 0, require = 0)
    private static ItemStack lensouls$captureDrop(ItemStack stack) {
        LenSouls.LOGGER.info("[Polaroid] capture: drop");
        if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem) capturedPhoto.set(stack);
        return stack;
    }

    @Inject(method = "printPhotograph", at = @At("RETURN"), require = 0)
    private static void lensouls$inject(
            CameraHolder holder, ItemStack cameraStack, Frame frame, CallbackInfo ci) {
        ItemStack photo = capturedPhoto.get();
        capturedPhoto.remove();

        if (!(holder.asHolderEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;

        String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
        if (exposureId == null) return;
        AbilityType ability = PhotoInjectionHandler.pollAbility(exposureId);
        LenSouls.LOGGER.info("[Polaroid] inject: ability={} photo={} hasEntities={}", ability, photo != null && !photo.isEmpty(), frame.entitiesInFrame() != null && !frame.entitiesInFrame().isEmpty());
        if (ability == null) return;

        if (photo == null || photo.isEmpty()) return;

        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("lensouls:injected")) return;

        boolean hasEntities = frame.entitiesInFrame() != null && !frame.entitiesInFrame().isEmpty();
        boolean doInject = true;

        // 能力窃取无实体/无效果 → 普通照片
        if (ability == AbilityType.ABILITY_STEAL) {
            String entityId = PhotoInjectionHandler.pollStolenEntity(
                    frame.identifier() != null ? frame.identifier().toString() : null);
            if (entityId == null || entityId.isEmpty() || !PhotographEffectRegistry.hasEffect(entityId)) {
                doInject = false;
            } else {
                tag.putBoolean("lensouls:ability_steal", true);
                tag.putString("lensouls:stolen_entity", entityId);
                tag.putBoolean("lensouls:photograph_curio", true);
            }
        }

        // 弱点透镜无实体 → 普通照片
        if (ability == AbilityType.WEAKNESS_LENS && !hasEntities) {
            doInject = false;
        }

        if (doInject) {
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
            }

            MutableComponent name = Component.literal("")
                    .append(Component.translatable(nameKey))
                    .append(Component.literal("照片"))
                    .withStyle(ChatFormatting.GREEN)
                    .withStyle(s -> s.withItalic(false).withBold(false));
            photo.set(DataComponents.CUSTOM_NAME, name);
        }

        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

}
