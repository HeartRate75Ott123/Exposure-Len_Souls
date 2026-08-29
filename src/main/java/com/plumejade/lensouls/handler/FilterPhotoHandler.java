package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.PotionFilterData;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.server.CameraInstances;
import io.github.mortuusars.exposure.data.Filters;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相机滤镜触发：手持已挂滤镜的相机自拍（或拍敌人）→ 施加对应滤镜效果 20s，相机进入 30s 冷却。
 * 不要求摄魂术附魔，与既有照片注入能力系统解耦；荒厄遗咒佩戴者无法触发。
 */
public class FilterPhotoHandler {

    /** 滤镜 id（exposure_expanded 命名空间）→ 施加在玩家身上的效果 */
    private static final Map<ResourceLocation, Holder<MobEffect>> SELF_EFFECTS = new HashMap<>();
    /** 敌人易伤滤镜（仅非自拍、画面内有敌人时生效） */
    private static final ResourceLocation ENEMY_FILTER = ResourceLocation.fromNamespaceAndPath("exposure_expanded", "spider");
    /** 每玩家滤镜拍摄冷却闸门（游戏刻）；保证 30s 内不重复触发，防御 Exposure 覆盖 MC 冷却。 */
    private static final Map<UUID, Long> lastFilterShot = new ConcurrentHashMap<>();
    /** 药水玻璃板独立冷却闸门（游戏刻）；300 刻，区别于 16 特殊滤镜的 600 刻。 */
    private static final Map<UUID, Long> lastGlassShot = new ConcurrentHashMap<>();

    private static void reg(String filter, Holder<MobEffect> effect) {
        SELF_EFFECTS.put(ResourceLocation.fromNamespaceAndPath("exposure_expanded", filter), effect);
    }

    static {
        reg("blobs", ModEffects.FILTER_BLOBS);
        reg("color_convolve", ModEffects.FILTER_COLOR_CONVOLVE);
        reg("sobel", ModEffects.FILTER_SOBEL);
        reg("pencil", ModEffects.FILTER_PENCIL);
        reg("antialias", ModEffects.FILTER_ANTIALIAS);
        reg("art", ModEffects.FILTER_ART);
        reg("bumpy", ModEffects.FILTER_BUMPY);
        reg("flip", ModEffects.FILTER_FLIP);
        reg("ntsc", ModEffects.FILTER_NTSC);
        reg("wobble", ModEffects.FILTER_WOBBLE);
        reg("scan_pincushion", ModEffects.FILTER_SCAN_PINCUSHION);
        reg("desaturate", ModEffects.FILTER_DESATURATE);
        reg("bits", ModEffects.FILTER_BITS);
        reg("deconverge", ModEffects.FILTER_DECONVERGE);
        reg("blur", ModEffects.FILTER_BLUR);
    }

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            if (FeatherHardmanHandler.hasHardman(player)) return;

            Frame frame = event.getFrame();
            if (frame == null) return;

            boolean selfie = frame.extraData().get(Frame.SELFIE).orElse(false);

            ItemStack hand = CameraInputHandler.getWieldedCamera(player);
            if (hand.isEmpty() || !(hand.getItem() instanceof CameraItem camera)) return;

            Optional<ResourceLocation> filterOpt = camera.getFilter(player.registryAccess(), hand)
                    .flatMap(f -> Filters.locationOf(player.registryAccess(), f));
            if (filterOpt.isEmpty()) return;
            ResourceLocation filter = filterOpt.get();

            long now = player.level().getGameTime();

            // 药水玻璃板：玻璃板本身已是相机滤镜，若其携带 POTION_FILTER_DATA 组件，
            // 则对自拍目标或入镜生物施加对应原版药水效果；独立 3s（60 刻）冷却，区别于 16 特殊滤镜
            var stored = hand.get(Exposure.DataComponents.FILTER);
            if (stored != null && !stored.isEmpty()) {
                var pdata = stored.getForReading().get(ModDataComponents.POTION_FILTER_DATA);
                if (pdata != null) {
                    if (now < lastGlassShot.getOrDefault(player.getUUID(), Long.MIN_VALUE) + 60) return;
                    applyPotionFilter(player, hand, pdata, selfie, event, now);
                    return;
                }
            }

            if (now < lastFilterShot.getOrDefault(player.getUUID(), Long.MIN_VALUE) + 120) return;

            // 敌人易伤：仅拍敌人（非自拍）时对其上易伤（对所有入镜非自己生物生效）；无生物则不消耗冷却
            if (filter.equals(ENEMY_FILTER)) {
                if (!selfie) {
                    boolean hasTarget = false;
                    for (LivingEntity e : event.getEntitiesInFrame()) {
                        if (e != player && e.isAlive()) { hasTarget = true; break; }
                    }
                    if (!hasTarget) return;
                    for (LivingEntity e : event.getEntitiesInFrame()) {
                        if (e != player && e.isAlive()) {
                            e.addEffect(new MobEffectInstance(ModEffects.FILTER_SPIDER, 400));
                        }
                    }
                    scheduleCooldown(hand, 120);
                    lastFilterShot.put(player.getUUID(), now);
                }
                return;
            }

            if (!selfie) return;
            Holder<MobEffect> effect = SELF_EFFECTS.get(filter);
            if (effect == null) return;

            if (effect == ModEffects.FILTER_ART) {
                applyRandomBuffs(player);
            } else {
                player.addEffect(new MobEffectInstance(effect, 400));
            }
            scheduleCooldown(hand, 120);
            lastFilterShot.put(player.getUUID(), now);
        } catch (Exception e) {
            LenSouls.LOGGER.error("[FilterPhoto] fail", e);
        }
    }

    /** 通过曝光的 CameraInstance 通道设置冷却：takePhoto 已将 deferredCooldown 设为自身值，
     *  本方法在 FrameAddedEvent（早于 onShutterClosed）覆盖为我们的刻数，快门关闭时曝光据此 addCooldown，
     *  确保相机真正进入冷却（不被 BASE_COOLDOWN=2 覆盖）。 */
    private static void scheduleCooldown(ItemStack cameraStack, int ticks) {
        CameraInstances.getOptional(cameraStack).ifPresent(inst -> inst.setDeferredCooldown(ticks));
    }

    /**
     * 药水玻璃板：施加携带的全部原版药水效果（各自等级与时长，按注入药水迁移）。
     * 自拍 → 施加给自己；否则 → 施加给入镜全部非自己且存活的实体。
     */
    private static void applyPotionFilter(ServerPlayer player, ItemStack hand, PotionFilterData data,
                                          boolean selfie, FrameAddedEvent event, long now) {
        List<MobEffectInstance> instances = new ArrayList<>();
        for (var e : data.effects()) {
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, e.effect());
            var effect = BuiltInRegistries.MOB_EFFECT.getHolder(key).orElse(null);
            if (effect == null) continue;
            instances.add(new MobEffectInstance(effect, e.duration(), e.amplifier()));
        }
        if (instances.isEmpty()) return;

        if (selfie) {
            // 自拍：目标为自己，必命中，记冷却
            instances.forEach(player::addEffect);
            scheduleCooldown(hand, 60);
            lastGlassShot.put(player.getUUID(), now);
        } else {
            // 非自拍：入镜无生物则不消耗冷却
            boolean hasTarget = false;
            for (LivingEntity ent : event.getEntitiesInFrame()) {
                if (ent != player && ent.isAlive()) { hasTarget = true; break; }
            }
            if (!hasTarget) return;
            // 先记冷却，避免下方效果施加异常被 onFrameAdded 的 try/catch 吞掉而跳过冷却
            scheduleCooldown(hand, 60);
            lastGlassShot.put(player.getUUID(), now);
            for (LivingEntity ent : event.getEntitiesInFrame()) {
                if (ent != player && ent.isAlive()) {
                    instances.forEach(ent::addEffect);
                }
            }
        }
    }

    /** #7 万象加持：随机 4 个原版正面效果，等级 2~3，持续 20s */
    private static void applyRandomBuffs(ServerPlayer player) {
        List<Holder<MobEffect>> pool = new ArrayList<>(List.of(
                MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE, MobEffects.DIG_SPEED,
                MobEffects.HEALTH_BOOST, MobEffects.JUMP, MobEffects.MOVEMENT_SPEED,
                MobEffects.REGENERATION, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING,
                MobEffects.ABSORPTION, MobEffects.SATURATION, MobEffects.LUCK, MobEffects.NIGHT_VISION));
        for (int i = pool.size() - 1; i > 0; i--) {
            int j = player.getRandom().nextInt(i + 1);
            Collections.swap(pool, i, j);
        }
        int count = Math.min(4, pool.size());
        for (int i = 0; i < count; i++) {
            int amp = 1 + player.getRandom().nextInt(2);
            player.addEffect(new MobEffectInstance(pool.get(i), 400, amp - 1));
        }
    }
}
