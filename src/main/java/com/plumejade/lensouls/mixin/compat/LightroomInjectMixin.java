package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.camera.frame.Photographer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
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

        String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
        if (exposureId == null) return;

        AbilityType ability = PhotoInjectionHandler.pollAbility(exposureId);
        if (ability == null) return;

        CompoundTag tag = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean("lensouls:injected")) return;

        boolean hasEntities = frame.entitiesInFrame() != null && !frame.entitiesInFrame().isEmpty();
        boolean doInject = true;

        if (ability == AbilityType.ABILITY_STEAL) {
            String entityId = PhotoInjectionHandler.pollStolenEntity(frame.identifier().toString());
            if (entityId == null || entityId.isEmpty() || !PhotographEffectRegistry.hasEffect(entityId)) {
                doInject = false;
            } else {
                tag.putBoolean("lensouls:ability_steal", true);
                tag.putString("lensouls:stolen_entity", entityId);
                tag.putBoolean("lensouls:photograph_curio", true);
            }
        }

        if (ability == AbilityType.WEAKNESS_LENS && !hasEntities) {
            doInject = false;
        }

        if (doInject) {
            tag.putBoolean("lensouls:injected", true);
            tag.putString("lensouls:ability_type", ability.getId());

            switch (ability) {
                case SPATIAL_WARP -> {
                    Vec3 pos = frame.extraData().get(Frame.POSITION).orElse(null);
                    if (pos != null) {
                        CompoundTag posTag = new CompoundTag();
                        posTag.putDouble("x", pos.x);
                        posTag.putDouble("y", pos.y);
                        posTag.putDouble("z", pos.z);
                        tag.put("lensouls:spatial_warp_pos", posTag);
                    }
                }
                case TEMPORAL_RECALL -> {
                    Photographer p = frame.photographer();
                    if (p != null && p.uuid() != null) {
                        ServerPlayer sp = net.neoforged.neoforge.server.ServerLifecycleHooks
                                .getCurrentServer().getPlayerList().getPlayer(p.uuid());
                        if (sp != null) {
                            TemporalSnapshot snapshot = TemporalSnapshot.capture(sp);
                            tag.put("lensouls:snapshot", snapshot.toTag());
                        }
                    }
                }
            }
        }

        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

    }
}
