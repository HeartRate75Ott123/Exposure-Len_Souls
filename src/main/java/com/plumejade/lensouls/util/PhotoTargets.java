package com.plumejade.lensouls.util;

import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.entity.PhantomDamageHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 「这次拍照要作用在谁身上」——<b>全部选取口径的唯一入口</b>。
 *
 * <p><b>为什么要有这一层</b>：以前每个玩法各自在入镜列表上写条件
 * （{@code ent != player}、{@code ent instanceof ServerPlayer}、{@code !(ent instanceof Player)}…），
 * 于是「谁算目标」散落在五六个 handler 里，改一处口径就得追着找另外几处，还很容易漏。
 * 现在分成三层，各管各的：
 * <ol>
 *   <li>{@link CameraVisibility}：<b>几何</b>——看得见吗（视锥 / 多采样 / 遮挡 / 射程）；</li>
 *   <li>{@link FrameEntities}：<b>入镜</b>——画面里有哪些活体（唯一硬规则：不含相机持有者）；</li>
 *   <li><b>本类</b>：<b>玩法策略</b>——这次要谁。每个调用点一行就能看出它选的是"谁"。</li>
 * </ol>
 * <p>
 * <b>不要</b>再往 {@code FrameEntities} 里加"过滤某某"：那会让"拍不到无人机/拍不到队友"这类
 * 玩法后果发生在<b>所有</b>消费方身上（帧列表是几何事实，不是策略）。
 *
 * <p><b>各玩法的既定口径</b>（用户口径，勿擅自合并）：
 * <ul>
 *   <li><b>滤镜 / 药水玻璃板</b>：多目标，<b>要玩家</b>——本来就是"给队友上增益"，无论什么能力，
 *       与能力拍照完全正交（自拍走 {@code Frame.SELFIE}，不靠实体列表）；</li>
 *   <li><b>能力窃取 / 弱点透镜</b>：单主体，<b>玩家不算主体</b>
 *       （玩家没有窃取效果条目；弱点透镜也不以玩家为拍摄对象）；</li>
 *   <li><b>时间定格</b>：多目标，<b>不要玩家</b>（含拍摄者本人）、
 *       <b>不要玩家驯服的宠物</b>、<b>不要本模组自己的召唤物</b>（幻灵 / 幻影幻翼）。</li>
 * </ul>
 * 所有方法都把 {@code viewer} 当显式参数：<b>"不要选到自己"是这一层的职责</b>，
 * 而不是每个 handler 自觉写 {@code ent != player}（漏一处就是"给自己上 buff"）。
 */
public final class PhotoTargets {

    private PhotoTargets() {
    }

    // ========== 单主体：记录进照片的那一个 ==========

    /** 帧内第一个<b>非玩家</b>实体（弱点透镜、能力窃取）。 {@code normalize} 可做「子部件→父体」追溯。 */
    public static <T extends LivingEntity> T subject(Entity viewer, Collection<T> frame,
                                                    UnaryOperator<T> normalize) {
        if (frame == null) return null;
        for (T candidate : frame) {
            if (candidate == null) continue;
            T resolved = normalize != null ? normalize.apply(candidate) : candidate;
            if (resolved == null || resolved == viewer) continue;
            if (resolved instanceof Player) continue;
            return resolved;
        }
        return null;
    }

    public static <T extends LivingEntity> T subject(Entity viewer, Collection<T> frame) {
        return subject(viewer, frame, null);
    }

    /**
     * 帧内第一个<b>带 {@code attacker_element} 活性</b>的实体（普通照片的元素活性组件）。
     * <p>
     * 这里<b>按数据包说话、不特判玩家</b>：数据里给谁配了活性，谁就是主体——
     * 没配的（包括绝大多数玩家）自然被跳过。要排除玩家就改数据包，不要在这里加规则。
     */
    public static <T extends LivingEntity> T subjectWithElement(Entity viewer, Collection<T> frame) {
        if (frame == null) return null;
        for (T candidate : frame) {
            if (candidate == null || candidate == viewer) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());
            if (id == null) continue;
            if (!AttackerElementLoader.getLevels(id).isEmpty()) return candidate;
        }
        return null;
    }

    // ========== 多目标：滤镜 / 药水玻璃板 ==========

    /** 除 viewer 外的全部活体（药水玻璃板：对入镜所有非自己实体施加药水效果）。 */
    public static <T extends LivingEntity> List<T> others(Entity viewer, Collection<T> frame) {
        List<T> out = new ArrayList<>();
        if (frame == null) return out;
        for (T e : frame) {
            if (e == null || e == viewer || !e.isAlive()) continue;
            out.add(e);
        }
        return out;
    }

    /** 除 viewer 外的<b>玩家</b>（16 个特殊滤镜非自拍：给队友上增益）。 */
    public static <T extends LivingEntity> List<T> otherPlayers(Entity viewer, Collection<T> frame) {
        List<T> out = new ArrayList<>();
        if (frame == null) return out;
        for (T e : frame) {
            if (e == null || e == viewer || !e.isAlive()) continue;
            if (!(e instanceof Player)) continue;
            out.add(e);
        }
        return out;
    }

    /** 除 viewer 外的<b>非玩家生物</b>（spider 滤镜：给敌人挂易伤）。 */
    public static <T extends LivingEntity> List<T> othersNonPlayer(Entity viewer, Collection<T> frame) {
        List<T> out = new ArrayList<>();
        if (frame == null) return out;
        for (T e : frame) {
            if (e == null || e == viewer || !e.isAlive()) continue;
            if (e instanceof Player) continue;
            out.add(e);
        }
        return out;
    }

    // ========== 时间定格 ==========

    /**
     * 可以定身的目标：<b>任何非友方单位</b>——口径统一在 {@link AllyFilter#isFriendlyUnit}：
     * 玩家（含拍摄者本人）、任意驯服宠物、自家召唤物（幻灵 / 幻翼）、
     * 以及任意第三方辅助作战单位（2026-10 起连辅助单位也不冻）。
     * <p>
     * 定格玩家的理由是玩法（定格玩家＝破坏 PvP 手感，且拍摄者本人绝不该被自己定住）；
     * 其余的理由是"友方不该被自己的招数锁住"，与弹幕免伤/选敌同口径
     * （口径与分类定义全在 {@link AllyFilter}，这里只调用）。
     */
    public static <T extends LivingEntity> List<T> freezable(Entity viewer, Collection<T> frame) {
        List<T> out = new ArrayList<>();
        if (frame == null) return out;
        for (T e : frame) {
            if (e == null || e == viewer || e.isRemoved()) continue;
            // 「友方」口径统一在 AllyFilter.isFriendly（大过滤 = 类别 + 同乘），视角 = 拍摄者。
            // 类别含：玩家（含拍摄者本人）+ 任意驯服宠物 + 自家召唤物（幻灵与随从、幻翼）
            // + 任意第三方辅助作战单位；同乘那一档顺带保证不会定住拍摄者自己骑着的坐骑。
            // 以后新增友方类别只需改 AllyFilter 一处，别在这里补 continue。
            if (AllyFilter.isFriendly(viewer, e)) continue;
            out.add(e);
        }
        return out;
    }
}
