package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yeti 定身专属适配（破刹 / 时间定格）。
 * <p>
 * 两项被动在暂停实体刻下会永久卡死，必须分别中和：
 * <ol>
 *   <li><b>高护甲</b>：{@code ServerConfiguration.YETI_ARMOR} 于生成时写死为属性基础值；
 *       定身期间清零、解冻时还原，让伤害走原版通道。</li>
 *   <li><b>受击动画计时器</b>（本次修复）：{@code YetiEntity.hurt} 的伤害闸门要求
 *       {@code DATA_HIT_ANIMTIME <= 13}（满值 {@code HURT_ANIMATION_TIME = 27}），
 *       而该计时器<b>只在 {@code YetiEntity.tick()} 内自减</b>。
 *       {@link BlockFactorysBossStunMixin} 在定身期间整段取消该重写的 tick，
 *       于是第一次命中写入的 27 被冻结 → 其后每一次 {@code hurt} 都被闸门拒绝，
 *       表现为「被定身后打一下就无法继续造成伤害」（连击全免伤）。</li>
 * </ol>
 * 处理方式：定身期间每次受击前把计时器压到 13（闸门阈值）。
 * 选 13 而非 0 是刻意的——{@code hurt} 只在计时器 <b>等于 0</b> 时才重新写 27，
 * 压到 13 既不触发重写（动画仍停在定身瞬间受击帧，不会自己往前播），
 * 又满足 {@code <= 13}，使定身期间每一击都能正常结算；
 * 解冻后计时器继续自减到 0，动画/后续命中节奏自动恢复正常。
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.yeti.YetiEntity", remap = false)
public abstract class YetiStunPassiveMixin {

    /** Yeti 伤害闸门阈值：{@code DATA_HIT_ANIMTIME <= 13} 才允许结算伤害。 */
    private static final int LENSOULS_YETI_HIT_GATE = 13;

    /**
     * {@code DATA_HIT_ANIMTIME} 声明在<b>父类</b> {@code AbstractBossEntity}（public static），
     * {@code @Shadow} 定位不到继承来的静态字段（实测 block_factorys_bosses 2.1.2：
     * "&#64;Shadow field DATA_HIT_ANIMTIME was not located in the target class YetiEntity"，
     * 整个 mixin 被跳过），因此改为反射取一次访问器。
     */
    private static final EntityDataAccessor<Integer> LENSOULS_HIT_ANIMTIME = lensoulsResolveHitAnimTime();

    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Integer> lensoulsResolveHitAnimTime() {
        try {
            Class<?> owner = Class.forName("net.unusual.block_factorys_bosses.entity.boss.AbstractBossEntity");
            return (EntityDataAccessor<Integer>) owner.getField("DATA_HIT_ANIMTIME").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Shadow
    protected SynchedEntityData entityData;

    @Unique
    private double lensoulsSavedArmor = -1;

    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void lensoulsNeutralizeArmor(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        AttributeInstance armor = ((LivingEntity) (Object) this).getAttribute(Attributes.ARMOR);
        if (armor == null) return;
        if (StunPauseHelper.isStunPaused(self)) {
            if (lensoulsSavedArmor < 0) lensoulsSavedArmor = armor.getBaseValue();
            armor.setBaseValue(0);
        } else if (lensoulsSavedArmor >= 0) {
            armor.setBaseValue(lensoulsSavedArmor);
            lensoulsSavedArmor = -1;
        }
    }

    /**
     * 定身期间解除受击动画闸门冻结（详见类注释第 2 条）。
     * 必须在 {@code hurt} 的 HEAD 注入：伤害闸门是方法体开头的分支判断。
     */
    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensoulsUnfreezeHitGate(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (!StunPauseHelper.isStunPaused(self)) return;
        EntityDataAccessor<Integer> accessor = LENSOULS_HIT_ANIMTIME;
        if (accessor == null) return;
        Integer animTime = this.entityData.get(accessor);
        if (animTime != null && animTime > LENSOULS_YETI_HIT_GATE) {
            this.entityData.set(accessor, LENSOULS_YETI_HIT_GATE);
        }
    }
}
