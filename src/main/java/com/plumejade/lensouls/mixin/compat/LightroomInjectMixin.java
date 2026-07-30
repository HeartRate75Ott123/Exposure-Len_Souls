package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 在 Lightroom createPrintResult 产出照片时直接打好标记。
 * 不碰 slot、不碰堆叠，只改返回的 ItemStack。
 */
@Mixin(targets = "io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity", remap = false)
public class LightroomInjectMixin {

    @Inject(method = "createPrintResult", at = @At("RETURN"), require = 0)
    private void lensouls$injectAtCreation(Frame frame,
                                            io.github.mortuusars.exposure.world.lightroom.PrintingProcess process,
                                            CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) return;
        if (!(result.getItem() instanceof io.github.mortuusars.exposure.world.item.PhotographItem)) return;

        Photographer photographer = frame.photographer();
        if (photographer == null || photographer.isEmpty()) return;
        UUID playerUuid = photographer.uuid();
        if (playerUuid == null) return;

        AbilityType ability = PhotoInjectionHandler.pollAbility(playerUuid);
        if (ability == null) return;

        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("lensouls:injected")) return;

        tag.putBoolean("lensouls:injected", true);
        tag.putString("lensouls:ability_type", ability.getId());

        // ABILITY_STEAL：从缓存读取被窃取实体 ID
        if (ability == AbilityType.ABILITY_STEAL) {
            String entityId = PhotoInjectionHandler.pollStolenEntity(frame.identifier().toString());
            if (entityId != null && !entityId.isEmpty()) {
                tag.putBoolean("lensouls:ability_steal", true);
                tag.putString("lensouls:stolen_entity", entityId);
                if (PhotographEffectRegistry.hasEffect(entityId)) {
                    tag.putBoolean("lensouls:photograph_curio", true);
                }
            }
        } else {
            String entityId = PhotoInjectionHandler.pollStolenEntity(frame.identifier().toString());
            if (entityId != null && !entityId.isEmpty()) {
                tag.putString("lensouls:stolen_entity", entityId);
                if (PhotographEffectRegistry.hasEffect(entityId)) {
                    tag.putBoolean("lensouls:photograph_curio", true);
                }
            }
        }

        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

    }
}
