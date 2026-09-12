package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Field Guide（图鉴模组，modid {@code fieldguide}）桥接。
 * <p>
 * 通过反射调用，无编译期依赖——图鉴未安装时全部方法安全空转。
 * <p>
 * <b>为什么不用图鉴自带的 Exposure 兼容</b>：它的 {@code ExposureCompat.unlockContentInFrame}
 * 每张照片会重新打 1 条准星射线 + <b>7×7=49 条跨 50°、长 256 格的射线</b>，并把命中的
 * <b>方块</b>（花草、地面等环境）也塞进解锁集合，再对每个目标做复合条目打分——既解锁了
 * 不该解锁的环境内容，又是严重卡顿的根因。本桥改为：
 * <ul>
 *   <li><b>只读生物</b>：数据源是相机帧内的存活生物（已被 {@code EntitiesInFrameMixin} 收口为
 *       「遇方块即止 + 硬射程上限」的优化视锥），不穿墙、不扫环境；</li>
 *   <li><b>零射线</b>：不再做任何额外 raycast，单张照片的开销与命中目标数同阶。</li>
 * </ul>
 * <b>解锁不受任何外部开关约束</b>：本桥从不读取图鉴的配置项（如
 * {@code exposureUnlockViaPhotograph}），也不接受图鉴条目自身的触发条件与前置限制——
 * 只要用见微知著能力拍了照，画面内的生物必定解锁。实现上先走图鉴官方入口
 * {@code tryUnlock(..., SCAN)}（保留它自己的动画/奖励/进度联动），若该入口被
 * 「条目触发条件（如 kill_to_unlock）」或「前置未解锁」拦下，则回落 direct unlock 强制写入。
 * <p>
 * 调用方（{@code FieldGuideExposureMixin}）负责取消图鉴原有的重扫描路径。
 */
public final class FieldGuideBridge {

    private static final String PROGRESS_MANAGER = "com.evandev.fieldguide.server.progress.FieldGuideProgressManager";
    private static final String PLAYER_PROGRESS = "com.evandev.fieldguide.server.progress.PlayerFieldGuideProgress";
    private static final String SERVER_MANAGER = "com.evandev.fieldguide.server.ServerFieldGuideManager";
    private static final String ENTRY_RESOLVER = "com.evandev.fieldguide.entry.EntryResolver";
    private static final String UNLOCK_TRIGGER = "com.evandev.fieldguide.api.EntryUnlockData$UnlockTrigger";

    private static boolean resolved;
    private static boolean unavailable;

    private static Method getProgressManager;
    private static Method getProgress;
    private static Method tryUnlock;
    private static Method forceUnlock;
    private static Method isUnlocked;
    private static Method getServerManager;
    private static Method getEntriesForTarget;
    private static Method getEntryId;
    private static Object scanTrigger;

    private FieldGuideBridge() {
    }

    /** 图鉴是否可用（已安装且反射签名匹配） */
    public static boolean isAvailable() {
        resolve();
        return !unavailable;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> progressManagerCls = Class.forName(PROGRESS_MANAGER);
            Class<?> playerProgressCls = Class.forName(PLAYER_PROGRESS);
            Class<?> serverManagerCls = Class.forName(SERVER_MANAGER);
            Class<?> entryResolverCls = Class.forName(ENTRY_RESOLVER);
            Class<?> triggerCls = Class.forName(UNLOCK_TRIGGER);

            getProgressManager = progressManagerCls.getMethod("getInstance");
            getProgress = progressManagerCls.getMethod("getProgress", ServerPlayer.class);
            tryUnlock = playerProgressCls.getMethod("tryUnlock", ServerPlayer.class,
                    ResourceLocation.class, String.class, triggerCls);
            // 官方入口被「条目触发条件 / 前置未解锁」拦下时的强制写入通道
            forceUnlock = playerProgressCls.getMethod("unlock", ServerPlayer.class,
                    ResourceLocation.class, String.class, boolean.class);
            isUnlocked = playerProgressCls.getMethod("isUnlocked", String.class);
            getServerManager = serverManagerCls.getMethod("getInstance");
            getEntriesForTarget = serverManagerCls.getMethod("getEntriesForTarget", Object.class);
            getEntryId = entryResolverCls.getMethod("getEntryId", Object.class);

            @SuppressWarnings({"unchecked", "rawtypes"})
            Object trigger = Enum.valueOf((Class<? extends Enum>) triggerCls, "SCAN");
            scanTrigger = trigger;
        } catch (Throwable t) {
            unavailable = true;
            LenSouls.LOGGER.info("[FieldGuide] 图鉴未安装或接口不匹配，跳过图鉴解锁集成（{}）", t.toString());
        }
    }

    /**
     * 只解锁给定生物在图鉴中的条目（环境内容天然豁免：非生物根本不在入参里）。
     * <p>
     * 不读取图鉴的任何配置项、不受条目触发条件与前置限制约束：被拦下时强制写入。
     *
     * @return 本次新解锁的条目数
     */
    public static int unlockEntities(ServerPlayer player, Collection<? extends LivingEntity> entities) {
        if (player == null || entities == null || entities.isEmpty()) return 0;
        if (!isAvailable()) return 0;

        Set<EntityType<?>> types = new LinkedHashSet<>();
        for (LivingEntity entity : entities) {
            EntityType<?> type = filterToMob(entity);
            if (type != null) types.add(type);
        }
        return unlockTypes(player, types);
    }

    /** 只保留「生物」：排除玩家与杂项实体（掉落物/箭/船/经验球等），方块花草不在其列 */
    private static EntityType<?> filterToMob(LivingEntity entity) {
        if (entity instanceof Player) return null;
        EntityType<?> type = entity.getType();
        if (type == null || type.equals(EntityType.PLAYER)) return null;
        MobCategory category = type.getCategory();
        if (category == MobCategory.MISC) return null;
        return type;
    }

    /** 按实体类型解锁图鉴条目 */
    public static int unlockTypes(ServerPlayer player, Collection<EntityType<?>> types) {
        if (player == null || types == null || types.isEmpty()) return 0;
        if (!isAvailable()) return 0;

        int unlocked = 0;
        try {
            Object progressManager = getProgressManager.invoke(null);
            if (progressManager == null) return 0;
            Object progress = getProgress.invoke(progressManager, player);
            if (progress == null) return 0;

            Object serverManager = getServerManager.invoke(null);
            if (serverManager == null) return 0;

            for (EntityType<?> type : types) {
                for (Object entry : entriesFor(serverManager, type)) {
                    ResourceLocation entryId = (ResourceLocation) getEntryId.invoke(null, entry);
                    if (entryId == null) continue;

                    boolean wasUnlocked = (Boolean) isUnlocked.invoke(progress, entryId.toString());

                    // 1) 先走官方入口：保留图鉴自己的解锁动画、奖励与「分类完成」联动
                    //    variantId 传 null：帧实体只带类型信息，与图鉴自身对帧实体的处理一致
                    tryUnlock.invoke(progress, player, entryId, null, scanTrigger);

                    // 2) 仍锁着说明被图鉴的条目触发条件（kill_to_unlock 等）或前置条目拦下。
                    //    「用能力拍了就解锁」是我们的硬承诺，直接强制写入，不受任何外部规则/配置约束。
                    if (!wasUnlocked && !(Boolean) isUnlocked.invoke(progress, entryId.toString())) {
                        forceUnlock.invoke(progress, player, entryId, null, true);
                    }

                    if (!wasUnlocked && (Boolean) isUnlocked.invoke(progress, entryId.toString())) {
                        unlocked++;
                    }
                }
            }
        } catch (Throwable t) {
            unavailable = true;
            LenSouls.LOGGER.warn("[FieldGuide] 解锁失败，后续不再尝试", t);
        }
        return unlocked;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> entriesFor(Object serverManager, EntityType<?> type) {
        try {
            List<Object> entries = (List<Object>) getEntriesForTarget.invoke(serverManager, type);
            return entries != null ? entries : List.of();
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }
}
