package com.plumejade.lensouls.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * <b>「驯服过的生物是否纳入相机拍摄主体选取」开关</b>（玩家级，默认<b>开启</b>）。
 * <p>
 * 架构照搬照片弹幕开关 {@link BarrageToggle}：客户端上报、<b>服务端权威</b>、
 * 状态落在玩家自己的 {@code persistentData}（NeoForge 会把它写进玩家存档 ⇒ 重进游戏记得上次的选择）、
 * 切换时在物品栏上方（动作栏）回一行带当前按键名的提示。
 * <p>
 * <b>键不存在 = 开启</b>（2026-10 用户口径）：驯服生物默认就纳入拍摄主体；
 * 关掉之后回到「驯服生物算友方 ⇒ 不算拍摄主体」的旧口径。
 * <p>
 * <b>只影响「拍摄主体」这一层</b>（{@link PhotoTargets#subject} 能力窃取、
 * {@link WeaknessLensPhoto#subjectEntityId} 弱点透镜）。其余友方判定
 * （弹幕误伤免伤 / 弹幕选敌 / 时间定格 / 次元枪）<b>不受本开关影响</b>——
 * 那些是"别伤自己人"，与"能不能给自己的宠物拍张照"是两回事。
 * <p>
 * 开关只豁免<b>驯服生物</b>（{@link AllyFilter#isTamedPet}，只认 {@code isTame()}、
 * 不分是谁的）；玩家、自家召唤物（幻灵 / 幻翼）、第三方辅助作战单位、同乘关系永远被跳过。
 * 分类定义仍全在 {@link AllyFilter}，本类只在其之上加一条豁免，不另立口径。
 */
public final class TamedPhotoSubject {

    /** 玩家 persistentData 键；缺失即视为开启（= 驯服生物纳入拍摄主体） */
    public static final String ENABLED_TAG = "lensouls:tamed_photo_subject";

    private TamedPhotoSubject() {
    }

    // ==================== 状态 ====================

    /**
     * 站在 {@code viewer} 的角度：驯服生物是否纳入拍摄主体。
     * 非玩家视角（拿不到玩家持久数据）返回 {@code false}（= 维持"友方不算主体"的降级口径）。
     */
    public static boolean isEnabled(Entity viewer) {
        return viewer instanceof Player player && isEnabled(player);
    }

    /** <b>键不存在 = 开启</b>：老存档与没切过的玩家默认把驯服生物当拍摄主体 */
    public static boolean isEnabled(Player player) {
        if (player == null) return false;
        CompoundTag tag = player.getPersistentData();
        return !tag.contains(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.getPersistentData().putBoolean(ENABLED_TAG, enabled);
    }

    /** 翻转并返回翻转后的值 */
    public static boolean flip(Player player) {
        boolean next = !isEnabled(player);
        setEnabled(player, next);
        return next;
    }

    // ==================== 选取（两个消费方的唯一收口） ====================

    /**
     * <b>类别层</b>跳过判定（不含同乘）：与 {@link AllyFilter#isFriendlyUnit} 同口径，
     * 唯一的差别是「开关打开时驯服生物不再算要跳过的友方」。
     * <p>消费方：{@link WeaknessLensPhoto#subjectEntityId}（弱点透镜主体）。
     */
    public static boolean skipFriendlyUnit(Entity viewer, Entity candidate) {
        if (candidate == null) return false;
        if (!AllyFilter.isFriendlyUnit(candidate)) return false;
        return !isTamedExempt(viewer, candidate);
    }

    /**
     * <b>大过滤</b>跳过判定（类别 + 同乘）：与 {@link AllyFilter#isFriendly} 同口径，
     * 唯一的差别同上。
     * <p>消费方：{@link PhotoTargets#subject}（能力窃取主体）。
     */
    public static boolean skipFriendly(Entity viewer, Entity candidate) {
        if (candidate == null) return false;
        if (!AllyFilter.isFriendly(viewer, candidate)) return false;
        return !isTamedExempt(viewer, candidate);
    }

    /**
     * 开关打开 + 该候选确实是驯服生物 ⇒ 不再按「友方」跳过，
     * 于是调用方会把它当成正常主体取用（并继续按原顺序取第一个）。
     */
    private static boolean isTamedExempt(Entity viewer, Entity candidate) {
        return AllyFilter.isTamedPet(candidate) && isEnabled(viewer);
    }
}
