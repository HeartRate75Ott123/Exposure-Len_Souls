package com.plumejade.lensouls.mixin.compat;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 堕落圣骑照片弹幕：取消「灵魂碎裂」对目标生命上限的削减。
 * <p>
 * <b>机制定位</b>（对实际安装的传奇怪物 2.1.20 jar 逐字节核对，参考源码为旧版本、字段名不同，曾把我带偏）：
 * <ul>
 *   <li>效果 id 是 {@code legendary_monsters:soul_fracture}（字段 {@code ModEffects.SOUL_FRACTURE}），
 *       其构造使用 {@code SoulEater} 类并挂 {@code Attributes.MAX_HEALTH} 负修饰符
 *       —— 即「减少生命上限」；</li>
 *   <li>施加点在 {@code SoulPillarEntity.damage(LivingEntity)} 的两个分支里，
 *       均为 {@code EntityUtil.applyStackingEffect(target, ModEffects.SOUL_FRACTURE, 1, 4, 200)}。</li>
 * </ul>
 * <b>作用范围</b>：仅当该尖刺是本模组照片弹幕生成的（{@code lensouls:photo_proj} 标记，
 * 由 {@code BossPhotoProjHelper.markAndSpawn} 打上）时才跳过这次效果施加；
 * <b>传奇怪物 BOSS 自己召唤的灵魂尖刺不受影响</b>，其伤害、回血、击退等其余逻辑也一律保留
 * （只掐掉生命上限削减这一件事）。
 * <p>
 * 用 {@link ThreadLocal} 传递「这个尖刺是不是我方的」：{@code @Redirect} 的目标是
 * <b>静态</b>方法调用，处理器必须是静态方法、拿不到 {@code this}；因此在同一个
 * {@code damage} 方法的 HEAD/RETURN 用非静态 {@code @Inject} 取标记、置入 ThreadLocal，
 * 再在静态重定向里读取。{@code damage} 的调用是单线程的服务端逻辑，且每个方法入口
 * 都会覆写，不存在跨实体的脏读。
 * <p>
 * {@code require = 1}：万一传奇怪物后续改动签名，宁可让 Mixin 明确报错，也不要像
 * 「静默不生效」那样让问题悄悄回来。
 * <p>
 * 本 mixin 位于 {@code lensouls.compat.mixins.json}（required=false），传奇怪物未安装时不加载。
 */
@Mixin(targets = "net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SoulPillarEntity", remap = false)
public class PaladinSoulPillarFractureMixin {

    /** 当前正在结算的尖刺是否由本模组照片弹幕生成 */
    @Unique
    private static final ThreadLocal<Boolean> LENSOULS_PHOTO_PILLAR = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "damage", at = @At("HEAD"), require = 1)
    private void lensouls$markPhotoPillar(LivingEntity target, CallbackInfo ci) {
        LENSOULS_PHOTO_PILLAR.set(
                ((Entity) (Object) this).getPersistentData().getBoolean("lensouls:photo_proj"));
    }

    @Inject(method = "damage", at = @At("RETURN"), require = 1)
    private void lensouls$clearPhotoPillar(LivingEntity target, CallbackInfo ci) {
        LENSOULS_PHOTO_PILLAR.remove();
    }

    /**
     * 拦截效果施加：我方照片弹幕的尖刺命中的目标，不再被挂上灵魂碎裂（生命上限削减）。
     * 其余效果（若有）与非我方尖刺一律原样放行。
     */
    @Redirect(method = "damage",
            at = @At(value = "INVOKE",
                    target = "Lnet/miauczel/legendary_monsters/util/EntityUtil;applyStackingEffect(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/core/Holder;III)V"),
            require = 1)
    private static void lensouls$skipSoulFracture(LivingEntity target, Holder<MobEffect> effect,
                                                  int amplifier, int maxStacks, int durationTicks) {
        boolean ours = Boolean.TRUE.equals(LENSOULS_PHOTO_PILLAR.get());
        if (ours && effect != null
                && effect == net.miauczel.legendary_monsters.effect.ModEffects.SOUL_FRACTURE) {
            return;
        }
        net.miauczel.legendary_monsters.util.EntityUtil.applyStackingEffect(
                target, effect, amplifier, maxStacks, durationTicks);
    }
}
