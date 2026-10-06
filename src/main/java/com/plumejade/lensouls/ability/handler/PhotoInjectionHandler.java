package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.BossEntityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.util.PhotoLog;
import com.plumejade.lensouls.util.PhotoTargets;
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

    /**
     * 拍照瞬间的缓存入口。这里每一个 {@code return} 都对应一种「照片最后变成普通照片」的成因，
     * 所以失败原因一律走 {@link PhotoLog}：<b>INFO 可见 + 按原因限流</b>
     * （同一原因 60 秒一条、全场 40 条上限；藏进 debug 等于排障时没人看，无脑 INFO 又会刷屏）。
     * <p>
     * 排障口径：<b>这一端</b>只回答「拍照时缓存成功了没有」——没选中能力 / 手上没相机 / 没摄魂术 /
     * 窃取时帧内没有非玩家实体；若这里都正常而照片仍是普通照片，断点就在打印端
     * （见 {@code PhotoInjector#inject} 的日志）。「按设计如此」的分支（即时生效能力不产照片）
     * 只走 {@link PhotoLog#debug}，不占 INFO 额度。
     */
    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            if (!(event.getCameraHolderEntity() instanceof ServerPlayer player)) return;
            // ⑤ 铁序·封镜（诅咒态）：Y≥50 时**不记录能力** ⇒ 出片是普通照片（只禁能力照片，不禁拍照）
            if (com.plumejade.lensouls.feather.CurseManager.on(player,
                    com.plumejade.lensouls.feather.CurseDefs.TIMECORE, 21)
                    && player.getY() >= 50.0) {
                return;
            }
            // ⑤ 铁序·封镜 的反转条件：在 Y≥200 处拍照（普通照片也算）
            com.plumejade.lensouls.handler.curse.TimecoreCurse.onPhotoTaken(player);
            var frame = event.getFrame();
            if (frame == null) {
                PhotoLog.info("cache-frame-null", () -> "未缓存：事件里的 frame 为空");
                return;
            }
            String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
            if (exposureId == null || exposureId.isEmpty()) {
                PhotoLog.info("cache-no-exposure-id", () -> "未缓存：帧没有 exposureId");
                return;
            }

            // 帧内第一个带元素活性的实体 → 缓存（普通拍照也注入组件）
            cacheFrameElements(player, exposureId, event.getEntitiesInFrame());

            // ① 羽·荒厄遗咒 e2「封器」：无法发动相机能力 = 只禁能力照片，普通拍照不受影响
            if (com.plumejade.lensouls.feather.CurseManager.on(player,
                    com.plumejade.lensouls.feather.CurseDefs.HARDMAN, 1)) {
                PhotoLog.info("cache-hardman", () -> "未缓存：① e2 封器未反转（exposureId=" + exposureId + "）");
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.lensouls.hardman.inject_fail"), true);
                return;
            }

            AbilityType ability = CameraAbilityStore.getSelected(player);
            if (ability == null) {
                PhotoLog.info("cache-no-ability", () -> "未缓存：相机上没有选中的能力（exposureId=" + exposureId
                        + "）——这一帧出片会是普通照片");
                return;
            }
            // 即时生效类能力（时间定格/要害打击/断魂）不注入照片——行为声明见 AbilityBehavior
            // 这条是「按设计如此」，不算故障 ⇒ 只进 debug.log，不占 INFO 额度
            if (!com.plumejade.lensouls.ability.AbilityBehavior.producesAbilityPhoto(ability)) {
                PhotoLog.debug("cache-instant-ability", () -> "未缓存：能力 " + ability.getId()
                        + " 属即时生效、按设计不产能力照片（exposureId=" + exposureId + "）");
                return;
            }

            ItemStack hand = CameraInputHandler.getWieldedCamera(player);
            if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), hand) <= 0) {
                PhotoLog.info("cache-no-enchant", () -> "未缓存：手持相机没有摄魂术（exposureId=" + exposureId
                        + ", ability=" + ability.getId() + ", 拍摄者此刻手上"
                        + (hand.isEmpty() ? "没有" : "有其它") + "相机）——常见于拍完立刻切手/换物品/进传送门");
                return;
            }

            // 按帧 ID 存储能力，后续切换能力不影响已拍帧
            LenSouls.LOGGER.debug("[PhotoInject] onFrameAdded: exposureId={} ability={}", exposureId, ability);
            pendingAbilities.put(exposureId, ability);

            // 见微知著：把画面中的生物自动解锁进图鉴（只读生物，花草等环境豁免；零额外射线）
            if (ability == AbilityType.WILD_GLIMPSE) {
                int unlocked = com.plumejade.lensouls.integration.FieldGuideBridge.unlockEntities(
                        player, event.getEntitiesInFrame());
                if (unlocked > 0) {
                    PhotoLog.info("glimpse-unlocked",
                            () -> "见微知著解锁图鉴 " + unlocked + " 条（exposureId=" + exposureId + "）");
                }
            }

            // 能力窃取：缓存被窃取实体 + Boss 判定（首领清单）
            if (ability == AbilityType.ABILITY_STEAL) {
                LivingEntity target = PhotoTargets.subject(
                        player, event.getEntitiesInFrame(), PhotoInjectionHandler::resolveToParent);
                if (target != null) {
                    String stolenId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
                    cacheStolenEntity(exposureId, stolenId);
                    bossFlagCache.put(exposureId, BossEntityLoader.isBoss(target));
                } else {
                    PhotoLog.info("cache-steal-no-subject", () -> "能力窃取未缓存主体：帧内没有「非玩家」实体"
                            + "（exposureId=" + exposureId + "）——这一帧出片会是普通照片");
                }
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhotoInject] fail", e);
        }
    }

    /** 缓存帧内第一个带元素活性实体的元素等级与 id（选取口径见 {@link PhotoTargets#subjectWithElement}） */
    private static void cacheFrameElements(LivingEntity viewer, String exposureId,
                                           List<LivingEntity> frameEntities) {
        LivingEntity e = PhotoTargets.subjectWithElement(viewer, frameEntities);
        if (e == null) return;
        ResourceLocation rl = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (rl == null) return;
        Map<ElementDamage, Integer> levels = AttackerElementLoader.getLevels(rl);
        if (levels.isEmpty()) return;
        elementEntityCache.put(exposureId, rl.toString());
        elementCache.put(exposureId, levels);
    }

    /**
     * 多部件实体（九头蛇头/娜迦尾等）子体追溯到父体，确保能力窃取取到 BOSS 本体（与削韧一致）。
     * <p>
     * 帧内实体按到相机距离排序，子体往往比父体中心离相机更近而排在最前；直接取
     * {@code get(0)} 会取到部件实体 id，导致 {@link PhotographEffectRegistry#hasEffect}
     * 查不到效果、退化为普通照片。
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
