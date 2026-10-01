package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.util.PartHitUtil;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 暮色森林·九头蛇：把「打在本体上、必然被吞掉」的伤害改派给部件。
 *
 * <p><b>问题：</b>{@code Hydra.hurt} 的实现是
 * {@code return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);}
 * —— 除「无视无敌」类伤害外<b>无条件吞伤害</b>。而九头蛇的设计是
 * {@code Hydra.isPickable() → false}（本体框太大）、{@code TFPart.isPickable() → true}（部件才是碰撞体），
 * 真正该走的是 {@code HydraPart.hurt → parent.attackEntityFromPart(...)}（头部减伤 / 头血 / 断裂都在那里）。
 * <p>于是所有「只查 {@code LivingEntity}」的弹幕与射线（传奇怪物的湮灭/能量射线、灾变死亡激光、
 * 斯库拉水波、原版唤魔者尖牙…）都会：命中本体巨大 AABB → {@code hydra.hurt} 返回 false → 伤害消失。
 * 次元枪之所以对九头蛇有效，是因为它自己遍历 {@code getParts()} 直接命中部件，绕开了本体这一层。
 *
 * <p><b>做法：</b>在本体 {@code hurt} 的 HEAD，当原逻辑注定返回 false 时，用
 * {@link PartHitUtil#hurtResolvingParts} 把这次命中改派给最近的部件（部件 hurt 会转发父实体）。
 * 一个注入点即覆盖<b>全部</b>弹幕与其它模组的射线/爆炸，且不碰任何全局查询。
 *
 * <p><b>闸门（避免削弱「必须打头」的设计）：</b>只处理「没有投射物本体」的伤害 ——
 * 传奇怪物的射线把 direct entity 写成施法者（{@code direct == entity}），而原版弓箭/投掷物的
 * direct 就是投射物本身 ⇒ <b>原版弓箭行为一字不变</b>（射身体照旧无效，必须射头）。
 * 无视无敌类伤害（{@code BYPASSES_INVULNERABILITY}）原本就能生效，也不插手。
 *
 * <p>暮色缺席时整条被跳过（compat 配置 {@code required:false} + 字符串 target + {@code require = 0}）。
 */
@Mixin(targets = "twilightforest.entity.boss.Hydra", remap = false)
public abstract class HydraPartDamageMixin {

    /**
     * 重入保护：部件 {@code hurt} 内部还会再走回父实体（{@code Hydra.attackEntityFromPart}），
     * 同一次伤害里绝不能再改派一次 —— 否则「改派 → 部件 → 本体 hurt → 再改派」自递归
     * （实测 StackOverflowError）。配合 {@link PartHitUtil#hurtNearestPart} 不回退本体，双重兜底。
     */
    private static final ThreadLocal<Boolean> LENSOULS_IN_REROUTE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void lensouls$rerouteToPart(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;   // 原本就能生效
        Entity direct = source.getDirectEntity();
        if (direct instanceof Projectile) return;                          // 原版弓箭/投掷物：保持原样
        if (direct != source.getEntity()) return;                          // 有独立投射物本体：不插手
        if (Boolean.TRUE.equals(LENSOULS_IN_REROUTE.get())) return;        // 已在改派链路里：不再插手
        LENSOULS_IN_REROUTE.set(Boolean.TRUE);
        try {
            if (PartHitUtil.hurtNearestPart((Entity) (Object) this, source, amount)) {
                cir.setReturnValue(true);
            }
        } finally {
            LENSOULS_IN_REROUTE.set(Boolean.FALSE);
        }
    }
}
