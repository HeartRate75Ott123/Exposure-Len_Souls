package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「无法造成韧性伤害」这类失败的<b>静默规则</b>（服务端）。
 * <p>
 * 用户定的规则：选中<b>能力窃取 / 弱点透镜 / 空间扭曲 / 时空回溯</b>时，不弹韧性相关弹窗、
 * 也不闪故障叉号 —— 这四个能力的照片是"拍内容"用的（偷能力、记录弱点、开扭曲、留回溯快照），
 * 玩家并不是在尝试削韧；这时候再提示"等级不足，无法造成韧性伤害"或者整屏闪一支叉，
 * 全是噪音。
 * <p>
 * 覆盖两处出口：
 * <ul>
 *   <li>{@code ToughnessPhotoHandler.failSound} —— 拍照削韧被镜头等级拦下时的提示；</li>
 *   <li>{@code VitalStrikeHandler} —— 白霸体挡下后的失败叉号（那里本来就要求选中要害打击，
 *       这层判断属于兜底，防止以后入口合并时把规则漏掉）。</li>
 * </ul>
 */
public final class ToughnessFailureGate {

    /** 这四个能力的照片不当作"尝试削韧"，失败提示一律静默 */
    private static final Set<AbilityType> SILENT_ABILITIES = EnumSet.of(
            AbilityType.ABILITY_STEAL,
            AbilityType.WEAKNESS_LENS,
            AbilityType.SPATIAL_WARP,
            AbilityType.TEMPORAL_RECALL
    );

    private ToughnessFailureGate() {
    }

    /** 当前选中的能力是否属于"不要韧性失败提示"的那一类 */
    public static boolean silenceFailure(ServerPlayer player) {
        if (player == null) return false;
        try {
            AbilityType selected = CameraAbilityStore.getSelected(player);
            if (selected == null) return false;
            return SILENT_ABILITIES.contains(selected);
        } catch (Throwable ignored) {
            // 读取相机组件失败时按"要提示"处理：宁可多一句，也别把真实拦截吞掉
            return false;
        }
    }
}
