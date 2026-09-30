package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.entity.PhantomDamageHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 幻灵击杀归属玩家：把死亡事件里那份 {@code DamageSource} 的「造成者」换成召唤者玩家。
 * <p>
 * <b>为什么必须 mixin</b>：1.21.1 的 {@code DamageSource} 是不可变对象，而 NeoForge 没有给
 * 任何改它的正规口子——{@code LivingDamageEvent.Pre} 只能改伤害值（{@code DamageContainer.source}
 * 是 {@code final} 且无 setter），{@code LivingAttackEvent} 在 1.21.1 已被移除。
 * 纯事件方案改不动它，只能在事件被构造出来的那一刻换。
 * <p>
 * <b>为什么在 {@code CommonHooks.onLivingDeath} 而不是 {@code die}</b>：
 * {@code LivingEntity.die} 的第一行就是 {@code CommonHooks.onLivingDeath(this, damageSource)}，
 * 之后才是 {@code getKillCredit()} / {@code dropAllDeathLoot()} / {@code dropExperience()}。
 * 命中判定的模组（FTB Quests 走 Architectury 的 {@code EntityEvent.LIVING_DEATH}）全部挂在这一个
 * 事件上，判据是 {@code source.getEntity() instanceof ServerPlayer}——只要这里换，击杀就算玩家的。
 * <p>
 * <b>为什么只在这一刻换</b>：{@code LivingDamageEvent} 阶段拿到的仍是原始 source。本模组里
 * 十几处「玩家造成」的判定（元素活性/弱点倍率、命中率掷骰、韧性减伤、灵魂口哨 -80%、
 * 照片弹幕的远程命中触发……）读的都是那一阶段的 source，因此全部维持原样：
 * 幻灵打人不会触发照片弹幕，幻灵伤害也不吃玩家的元素加成与口哨减免。
 * <p>
 * 改写口径见 {@link PhantomDamageHandler#creditOwner}：只换造成者，<b>直接实体保持幻灵</b>、
 * 伤害类型原样，所以自家读直接实体的判定（自伤保护、弹幕标记、穿透伤害档位、伤害类型标签）
 * 一律不受影响。
 * <p>
 * {@code require} 用项目默认的 1：签名一旦对不上就在启动时崩，而不是静默退化成
 * 「击杀不算玩家的」——后者正是这个 mixin 要消灭的行为。
 * <p>
 * {@code remap = false}：{@code CommonHooks} / {@code LivingDeathEvent} 是 NeoForge 自己的类，
 * 从不混淆，交给 remapper 只会去找不存在的混淆名。
 */
@Mixin(value = CommonHooks.class, remap = false)
public class PhantomKillCreditMixin {

    // 目标 CommonHooks#onLivingDeath 是 static，回调也必须 static，
    // 否则启动期抛 InvalidInjectionException: non-static callback method ... has a static target
    @Redirect(
            method = "onLivingDeath",
            at = @At(value = "NEW",
                    target = "Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;",
                    remap = false),
            remap = false,
            require = 1)
    private static LivingDeathEvent lensouls$creditPhantomKillToOwner(LivingEntity entity, DamageSource src) {
        return new LivingDeathEvent(entity, PhantomDamageHandler.creditOwner(src, entity));
    }
}
