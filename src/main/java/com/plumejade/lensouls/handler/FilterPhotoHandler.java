package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import io.github.mortuusars.exposure.data.Filters;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
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
            if (now < lastFilterShot.getOrDefault(player.getUUID(), Long.MIN_VALUE) + 600) return;

            // 敌人易伤：仅拍敌人（非自拍）时对其上易伤
            if (filter.equals(ENEMY_FILTER)) {
                if (!selfie) {
                    for (LivingEntity e : event.getEntitiesInFrame()) {
                        if (e != player && e.isAlive()) {
                            e.addEffect(new MobEffectInstance(ModEffects.FILTER_SPIDER, 400));
                            break;
                        }
                    }
                    scheduleCooldown(player, hand.getItem());
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
            scheduleCooldown(player, hand.getItem());
            lastFilterShot.put(player.getUUID(), now);
        } catch (Exception e) {
            LenSouls.LOGGER.error("[FilterPhoto] fail", e);
        }
    }

    /** 相机快门关闭时会把自己的冷却覆盖为 2 tick（在 FrameAddedEvent 之后执行），故延后一 tick 写入 600，确保后写生效。 */
    private static void scheduleCooldown(ServerPlayer player, Item cooldownItem) {
        player.server.execute(() -> player.getCooldowns().addCooldown(cooldownItem, 600));
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
