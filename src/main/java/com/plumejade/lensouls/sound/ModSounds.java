package com.plumejade.lensouls.sound;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, LenSouls.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> GUN_SHOOT =
            SOUNDS.register("gun.shoot", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gun.shoot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GUN_MODE_CHANGE =
            SOUNDS.register("gun.mode_change", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gun.mode_change")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GUN_BULLET_CHANGE =
            SOUNDS.register("gun.bullet_change", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gun.bullet_change")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GRAVITY_SHOOT =
            SOUNDS.register("gravity.shoot", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gravity.shoot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TOUGHNESS_CHANGE =
            SOUNDS.register("boss.toughness_change", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss.toughness_change")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TOUGHNESS_FAIL =
            SOUNDS.register("boss.toughness_fail", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "boss.toughness_fail")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HEAL_USE =
            SOUNDS.register("heal.use", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "heal.use")));

    // ========== N公司2级员工 Boss 音效 ==========

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_HIT1 =
            SOUNDS.register("level2_staff.hit1", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.hit1")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_HIT2 =
            SOUNDS.register("level2_staff.hit2", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.hit2")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_HIT3 =
            SOUNDS.register("level2_staff.hit3", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.hit3")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_SPIKE_HIT =
            SOUNDS.register("level2_staff.spike_hit", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.spike_hit")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_CAMERA_SHOOT =
            SOUNDS.register("level2_staff.camera_shoot", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.camera_shoot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LEVEL2_STAFF_BGM =
            SOUNDS.register("level2_staff.bgm", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "level2_staff.bgm")));

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
