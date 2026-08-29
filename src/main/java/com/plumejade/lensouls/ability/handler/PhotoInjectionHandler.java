package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhotoInjectionHandler {

    /** 帧标识符 → 拍摄时的能力（独立存储，互不覆盖） */
    private static final Map<String, AbilityType> pendingAbilities = new ConcurrentHashMap<>();
    private static final Map<String, String> stolenEntityCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> bossFlagCache = new ConcurrentHashMap<>();
    /** 帧标识符 → 帧内带元素活性实体的元素等级（普通拍照也注入照片组件） */
    private static final Map<String, Map<ElementDamage, Integer>> elementCache = new ConcurrentHashMap<>();
    private static final Map<String, String> elementEntityCache = new ConcurrentHashMap<>();

    public static Boolean pollBoss(String exposureId) {
        return exposureId != null ? bossFlagCache.remove(exposureId) : null;
    }

    /** 消费帧内活性实体信息（元素等级 + 实体 id） */
    public static Map<ElementDamage, Integer> pollElementLevels(String exposureId) {
        return exposureId != null ? elementCache.remove(exposureId) : null;
    }

    public static String pollElementEntity(String exposureId) {
        return exposureId != null ? elementEntityCache.remove(exposureId) : null;
    }

    public static void cacheStolenEntity(String exposureId, String entityId) {
        if (exposureId != null && !exposureId.isEmpty()) stolenEntityCache.put(exposureId, entityId);
    }

    public static String pollStolenEntity(String exposureId) {
        return exposureId != null ? stolenEntityCache.remove(exposureId) : null;
    }

    /** 按帧标识符取能力并删除 */
    public static AbilityType pollAbility(String exposureId) {
        return exposureId != null ? pendingAbilities.remove(exposureId) : null;
    }

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            var frame = event.getFrame();
            if (frame == null) return;
            String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
            if (exposureId == null || exposureId.isEmpty()) return;

            // 羽·荒厄遗咒：残存魔力被吞噬，无法发动相机能力
            if (FeatherHardmanHandler.hasHardman(player)) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.lensouls.hardman.inject_fail"), true);
                return;
            }

            // 帧内第一个带元素活性的实体 → 缓存（普通拍照也注入组件）
            cacheFrameElements(exposureId, event.getEntitiesInFrame());

            AbilityType ability = CameraAbilityStore.getSelected(player);
            if (ability == null) return;
            if (ability == AbilityType.TIME_STOP || ability == AbilityType.VITAL_STRIKE || ability == AbilityType.SOUL_SEVER) return;

            ItemStack hand = CameraInputHandler.getWieldedCamera(player);
            if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), hand) <= 0) return;

            // 按帧 ID 存储能力，后续切换能力不影响已拍帧
            LenSouls.LOGGER.info("[PhotoInject] onFrameAdded: exposureId={} ability={}", exposureId, ability);
            pendingAbilities.put(exposureId, ability);

            // 能力窃取：缓存被窃取实体 + Boss 判定（首领清单）
            if (ability == AbilityType.ABILITY_STEAL) {
                var frameEntities = event.getEntitiesInFrame();
                if (frameEntities != null && !frameEntities.isEmpty()) {
                    // 多部件 boss（九头蛇头/娜迦尾等）子体离相机更近、排在帧首——
                    // 追溯到父体，确保取到的 id 是 BOSS 本体（与削韧一致），否则按部件 id 查不到效果。
                    LivingEntity target = resolveToParent(frameEntities.get(0));
                    String stolenId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
                    cacheStolenEntity(exposureId, stolenId);
                    bossFlagCache.put(exposureId, BossEntityLoader.isBoss(target));
                }
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhotoInject] fail", e);
        }
    }

    /** 缓存帧内第一个带元素活性实体的元素等级与 id */
    private static void cacheFrameElements(String exposureId, List<LivingEntity> frameEntities) {
        if (frameEntities == null || frameEntities.isEmpty()) return;
        for (LivingEntity e : frameEntities) {
            ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (rl == null) continue;
            Map<ElementDamage, Integer> levels = AttackerElementLoader.getLevels(rl);
            if (!levels.isEmpty()) {
                elementEntityCache.put(exposureId, rl.toString());
                elementCache.put(exposureId, levels);
                return;
            }
        }
    }

    /**
     * 多部件实体（九头蛇头/娜迦尾等）子体追溯到父体，确保能力窃取取到 BOSS 本体（与削韧一致）。
     * <p>
     * 帧内实体按到相机距离排序，子体往往比父体中心离相机更近而排在最前；直接取 {@code get(0)}
     * 会取到部件实体 id，导致 {@link PhotographEffectRegistry#hasEffect} 查不到效果、退化为普通照片。
     */
    private static LivingEntity resolveToParent(LivingEntity entity) {
        // 子部件（九头蛇头/娜迦尾等）实现 PartEntity，追溯到父体
        if ((Entity) entity instanceof PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent instanceof LivingEntity le) return le;
        }
        return entity;
    }
}
