package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.boss.BlockFactorysSyncData;
import com.plumejade.lensouls.boss.StunPauseHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Yeti 定身专属适配（破刹 / 时间定格）。
 * <p>
 * <b>背景</b>：{@link BlockFactorysBossStunMixin} 在定身期间整段取消 {@code YetiEntity.tick()}，
 * 而该 boss 的伤害闸门全部读「只在 tick 内推进」的状态，于是第一次命中之后闸门永久关闭，
 * 表现为「定身后打一下，之后完全打不动」（连击全被 {@code hurt} 直接 {@code return false}）。
 * <p>
 * <b>踩过的两个坑（务必不要再犯）</b>：
 * <ol>
 *   <li>{@code @Shadow SynchedEntityData entityData} 定位失败 —— {@code entityData} 声明在祖父类 {@code Entity}，
 *       {@code @Shadow} 找不到就直接抛 {@code InvalidMixinException}，<b>整个 mixin 被跳过</b>
 *       （护甲清零与闸门解冻一起失效，日志里只有一行 WARN，游戏内毫无提示）。
 *       现改用 {@code ((LivingEntity) self).getEntityData()}（{@code Entity.getEntityData()} 是 public），全程不用 {@code @Shadow}。</li>
 *   <li>把访问器解析写成 <b>mixin 包内的嵌套类</b> —— Mixin 类加载器禁止引用 mixin 包内未注册为 mixin 的类，
 *       第一次受击即 {@code IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly}，
 *       打断服务端 tick 循环（表现为「命中瞬间卡死」）。辅助类一律放普通包：{@link BlockFactorysSyncData}。</li>
 * </ol>
 * <p>
 * <b>YetiEntity.hurt 的闸门</b>（反编译 2.1.2 确认，条件为「与」关系，任一不满足即 {@code return false}）：
 * <pre>
 *   DATA_IS_ENRAGED != 3 && DATA_IS_ENRAGED != 4
 *   && !FALL && !DROWN && !FALLING_ANVIL && !WITHER && !WITHER_SKULL
 *   && DATA_SPAWN_ANIMTIME &lt;= 0
 *   && DATA_HIT_ANIMTIME &lt;= 13
 *   && (DATA_GROUNDSMASH_ANIMTIME &lt;= 0 || DATA_GROUNDSMASH_ANIMTIME &gt;= 119)
 * </pre>
 * 其中三项都是「tick 内推进、定身即冻结」的状态，本 mixin 在受击前逐个摆平：
 * <ol>
 *   <li>{@code DATA_HIT_ANIMTIME}（父类 {@code AbstractBossEntity}）：受击时写 27，只在 tick 内自减。
 *       压到 13 —— 既满足 {@code <= 13}，又<b>不会</b>触发 {@code if (DATA_HIT_ANIMTIME == 0) 写 27} 的重置分支
 *       （所以不能压到 0），动画也保持在定身瞬间的受击帧。</li>
 *   <li>{@code DATA_GROUNDSMASH_ANIMTIME}（本类）：砸地动画计时器，0→119 递增，同样只在 tick 内走。
 *       定身卡在 1..118 就会永久拒绝伤害；置 0 让它满足 {@code <= 0}（副作用是这次砸地在定身结束后不会继续，
 *       但定身本来就该打断 boss 动作）。</li>
 *   <li>{@code DATA_SPAWN_ANIMTIME}（父类）：出场动画期间免疫伤害。正常玩法不可能在出场期破刹
 *       （那时打不动、也攒不出韧性），这里只是把闸门补齐，防止将来改动后留坑。</li>
 *   <li>{@code DATA_IS_ENRAGED == 3/4}：半血狂暴过渡态，此时 {@code hurt} 直接 {@code return false}，
 *       而 {@code 3 → 4 → 5} 的推进写在 {@code baseTick()}（同样被冻结）→ 定身期间永远拒绝伤害。
 *       处理为：定身期间把 3/4 临时当作 5（过渡已完成）让本次伤害成立，{@code hurt} 返回时还原原值，
 *       不破坏 boss 自己的狂暴阶段；选 5 而不是 2 是为了避开 {@code if (enrageFlag == 2 && 血量过半)}
 *       那段「再次触发狂暴过渡（给周围 32 格挂迟缓/抗性）」的分支。</li>
 * </ol>
 * 其余闸门（摔落/溺水/铁砧/凋零来源）与定身无关，不动。
 */
@Mixin(targets = "net.unusual.block_factorys_bosses.entity.boss.yeti.YetiEntity", remap = false)
public abstract class YetiStunPassiveMixin {

    /** Yeti 伤害闸门阈值：{@code DATA_HIT_ANIMTIME <= 13} 才允许结算伤害。 */
    private static final int LENSOULS_YETI_HIT_GATE = 13;

    /** 砸地闸门的放行上界：{@code DATA_GROUNDSMASH_ANIMTIME >= 119} 视为动画收尾。 */
    private static final int LENSOULS_YETI_SMASH_GATE = 119;

    /** 狂暴过渡完成态（反编译：3 过渡开始 → 4 动画中 → 5 已狂暴）。 */
    private static final int LENSOULS_YETI_ENRAGED_DONE = 5;

    private static Integer read(SynchedEntityData data, EntityDataAccessor<Integer> accessor) {
        return accessor == null ? null : data.get(accessor);
    }

    private static void force(SynchedEntityData data, EntityDataAccessor<Integer> accessor,
                              java.util.function.IntPredicate blocker, int value) {
        Integer current = read(data, accessor);
        if (current != null && blocker.test(current)) {
            data.set(accessor, value);
        }
    }

    /** 本次受击被临时改写的狂暴相位原值（-1 = 未改写）；{@code @Unique} 即每个实体一份。 */
    @Unique
    private int lensoulsSavedEnrage = -1;

    /**
     * 定身期间逐道打开 {@code hurt} 的伤害闸门（详见类注释）。
     * 必须注入 HEAD：闸门在方法体开头一次性判完。
     */
    @Inject(method = "hurt", at = @At("HEAD"), remap = false, require = 0)
    private void lensoulsOpenDamageGates(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!StunPauseHelper.isStunPaused(self)) return;
        SynchedEntityData data = self.getEntityData();

        force(data, BlockFactorysSyncData.hitAnimtime(), time -> time > LENSOULS_YETI_HIT_GATE, LENSOULS_YETI_HIT_GATE);
        force(data, BlockFactorysSyncData.groundSmashAnimtime(),
                time -> time > 0 && time < LENSOULS_YETI_SMASH_GATE, 0);
        force(data, BlockFactorysSyncData.spawnAnimtime(), time -> time > 0, 0);

        Integer enraged = read(data, BlockFactorysSyncData.enraged());
        if (enraged != null && (enraged == 3 || enraged == 4)) {
            lensoulsSavedEnrage = enraged;
            data.set(BlockFactorysSyncData.enraged(), LENSOULS_YETI_ENRAGED_DONE);
        }
    }

    /** {@code hurt} 的所有返回路径统一还原被临时改写的狂暴相位。 */
    @Inject(method = "hurt", at = @At("RETURN"), remap = false, require = 0)
    private void lensoulsRestoreEnrage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (lensoulsSavedEnrage < 0) return;
        SynchedEntityData data = ((LivingEntity) (Object) this).getEntityData();
        EntityDataAccessor<Integer> enraged = BlockFactorysSyncData.enraged();
        if (enraged != null) {
            data.set(enraged, lensoulsSavedEnrage);
        }
        lensoulsSavedEnrage = -1;
    }

    /**
     * 定身期间把 {@code ServerConfiguration.YETI_ARMOR} 写进属性基础值的高护甲清零，解冻还原。
     * <p>
     * 注意注入点是 {@code tick} 的 RETURN，而定身期间 {@code tick} 已被
     * {@link BlockFactorysBossStunMixin} 在 HEAD 整段取消 —— 也就是说本方法在定身期间<b>实际不生效</b>
     * （定身时护甲照旧）；它只在「未被取消的 tick」上做「不在定身 → 还原」这一半。
     * 保持现状是有意的：改成在受击前清零会实打实提高定身期的伤害，属于另一件事，未经测试不擅自引入。
     */
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

    @Unique
    private double lensoulsSavedArmor = -1;
}
