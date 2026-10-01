package com.plumejade.lensouls.util;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 多子部件实体的命中工具 —— 把次元枪 {@code GunBulletEntity.checkEntity} 里那套「部件优先」算法抽出来共用
 * （此前仓里有三份拷贝：{@code GunBulletEntity} / {@code AimTargetUtil} / {@code CameraVisibility}）。
 *
 * <p><b>为什么需要它：</b>暮色九头蛇这类多部件 BOSS 用「本体不可命中、部件才是碰撞体」的设计
 * （{@code TFPart.isPickable() → true}、{@code Hydra.isPickable() → false}），而不少弹幕/射线的索敌是
 * {@code getEntitiesOfClass(LivingEntity.class, …)} —— 部件是 {@link PartEntity}、<b>不是</b> LivingEntity，
 * 于是永远进不了候选；即使打中本体，本体也可能把伤害吞掉
 * （{@code Hydra.hurt} 为 {@code src.is(BYPASSES_INVULNERABILITY) && super.hurt(...)}）。
 *
 * <p>两个方向：
 * <ul>
 *   <li>{@link #clipParts} / {@link #nearestPart} —— 射线 / 线段方向；</li>
 *   <li>{@link #livingTargetsInBox} —— AoE 方向（按包围盒取目标时把部件折算成父实体并去重）。</li>
 * </ul>
 */
public final class PartHitUtil {

    private PartHitUtil() {
    }

    /** 部件链的终点（普通实体返回自身） */
    public static Entity resolveRoot(Entity entity) {
        Entity cur = entity;
        for (int guard = 0; cur instanceof PartEntity<?> part && guard < 8; guard++) {
            Entity parent = part.getParent();
            if (parent == null || parent == cur) break;
            cur = parent;
        }
        return cur;
    }

    private static Entity e(PartEntity<?> part) {
        return (Entity) part;
    }

    /** 与次元枪同口径：薄部件（最小边 < 0.6）向外补 0.3，避免细长部件被线段擦过 */
    private static AABB partBox(PartEntity<?> part) {
        AABB box = e(part).getBoundingBox();
        double minDim = Math.min(Math.min(box.getXsize(), box.getYsize()), box.getZsize());
        return minDim < 0.6 ? box.inflate(0.3) : box;
    }

    /** 与线段 {@code from→to} 相交的最近部件；没有部件或没命中返回 {@code null}（调用方应让弹丸继续飞） */
    public static PartEntity<?> clipParts(Entity victim, Vec3 from, Vec3 to) {
        PartEntity<?>[] parts = victim.getParts();
        if (parts == null || parts.length == 0) return null;
        PartEntity<?> best = null;
        double bestDist = Double.MAX_VALUE;
        for (PartEntity<?> part : parts) {
            if (part == null || !e(part).isAlive()) continue;
            AABB box = partBox(part);
            Optional<Vec3> clip = box.clip(from, to);
            double d;
            if (clip.isPresent()) {
                d = clip.get().distanceToSqr(from);
            } else if (box.contains(from)) {
                d = 0.0D;
            } else {
                continue;
            }
            if (d < bestDist) {
                bestDist = d;
                best = part;
            }
        }
        return best;
    }

    /** 离 {@code at} 最近的部件；{@code at} 为 {@code null} 时退化为离本体中心最近 */
    public static PartEntity<?> nearestPart(Entity victim, Vec3 at) {
        PartEntity<?>[] parts = victim.getParts();
        if (parts == null || parts.length == 0) return null;
        Vec3 anchor = at != null ? at
                : victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        PartEntity<?> best = null;
        double bestDist = Double.MAX_VALUE;
        for (PartEntity<?> part : parts) {
            if (part == null || !e(part).isAlive()) continue;
            double d = e(part).getBoundingBox().getCenter().distanceToSqr(anchor);
            if (d < bestDist) {
                bestDist = d;
                best = part;
            }
        }
        return best;
    }

    /**
     * 把一次命中交给「离伤害来源最近的部件」处理：部件的 {@code hurt} 内部会转发父实体
     * （{@code HydraPart → attackEntityFromPart}、{@code NagaSegment → 父体 ×2/3}），并跑本家的
     * 头部减伤 / 头血 / 断裂逻辑。
     *
     * <p><b>刻意不回退到 {@code victim.hurt}（踩过的坑：StackOverflowError）：</b>
     * 本方法就是从受害者<b>自己的 {@code hurt} 里</b>调用的（见 {@code HydraPartDamageMixin}）。
     * 一旦「部件拒收就回退 {@code victim.hurt}」，调用链会变成
     * 「改派给部件 → 部件转发父实体 → 又进本体的 hurt → 我们的注入再改派…」的无限递归
     * （实测直接 StackOverflowError 崩客户端）。部件拒收（如正在无敌帧内）时直接返回 false，
     * 由调用方决定后续（九头蛇那里就是保持原有的「吞掉」行为）。
     *
     * @return 部件是否接受了这次伤害
     */
    public static boolean hurtNearestPart(Entity victim, DamageSource source, float amount) {
        PartEntity<?> part = nearestPart(victim, source.getSourcePosition());
        return part != null && e(part).hurt(source, amount);
    }

    /**
     * 包围盒内的「活体目标」：普通 {@link LivingEntity} + 由部件折算出的父实体（去重、保序）。
     * 供 AoE / 范围技能使用 —— 只查 {@code getEntitiesOfClass(LivingEntity.class, box)} 会漏掉多部件 BOSS。
     */
    public static List<LivingEntity> livingTargetsInBox(Level level, AABB box) {
        List<LivingEntity> out = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box));
        for (Entity entity : level.getEntities((Entity) null, box)) {
            if (!(entity instanceof PartEntity<?> part)) continue;
            Entity root = resolveRoot(part);
            if (root instanceof LivingEntity living && living.isAlive() && !out.contains(living)) {
                out.add(living);
            }
        }
        return out;
    }
}
