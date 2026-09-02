package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.animation.Level2StaffBossAnimations;
import com.plumejade.lensouls.animation.Level2StaffBossAnimations.MeleeAnim;
import com.plumejade.lensouls.animation.Level2StaffBossAnimations.SpikeAnim;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * N公司2级员工（level2_staff_boss）—— 测试版 Boss 战斗逻辑（服务端驱动）。
 * <p>
 * 近战阶段：靠近玩家至 ~2 格触发一次 hit1/hit2/hit3（随机），
 * 动画行进到对应窗口（hit3 0.42~0.67s / hit2 0.38~0.63s / hit1 0.33~0.5s）
 * 且目标仍在交互距离内则造成伤害并播放对应音效（响度/音高浮动）。
 * <p>
 * 每累计造成 5 次近战伤害 → 远离玩家；距离 6~9 格时连续释放两次 spike
 * （spike_hit 在 0.75s、spike_hit2 在 0.9s 释放一次远程 + 音效），两次后重新贴近。
 * 远程为一道自身体中心向玩家的黑烟粒子射线，命中玩家造成 10 点魔法伤害。
 * <p>
 * 玩家距离 ≥9 格时尝试 camera_shoot（动画起即播音效，响度/音高浮动），放完再次贴近。
 * <p>
 * 动画仅一条控制器：移动播 walk，攻击经 triggerAnim 播放整段（播完自动回待机/行走）。
 */
public class Level2StaffBossEntity extends Monster implements GeoEntity {

    // 状态机
    private static final int ST_IDLE = 0;      // 决策
    private static final int ST_APPROACH = 1;  // 向玩家移动
    private static final int ST_MELEE = 2;     // 近战动画进行中
    private static final int ST_RETREAT = 3;   // 远离玩家直到 6~9 格
    private static final int ST_SPIKE = 4;     // spike 远程释放中
    private static final int ST_CAMERA = 5;    // camera_shoot 播放中

    /** 近战触发距离（中心水平距离） */
    private static final double MELEE_RANGE = 2.0D;
    /** 近战造成伤害计数阈值 → 触发撤退 */
    private static final int MELEE_HITS_TO_RETREAT = 5;
    /** 撤退目标距离下/上界 */
    private static final double RETREAT_MIN = 6.0D;
    private static final double RETREAT_MAX = 9.0D;
    /** camera_shoot 触发距离 */
    private static final double CAMERA_RANGE = 9.0D;
    /** 中距原地 spike 触发距离下界（3 ≤ dist < 9，不含 9——9 及以上归 camera） */
    private static final double SPIKE_MID_MIN = 3.0D;
    /** 魔法射线每 tick 推进距离（格） */
    private static final double RAY_SPEED = 0.7D;
    /** 魔法伤害 */
    private static final float RAY_DAMAGE = 10.0F;

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    /** 同步数据：服务端写移动状态，客户端动画据此播 walk */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> MOVING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    Level2StaffBossEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    /** 原版 Boss 血条（顶部显示） */
    private final net.minecraft.server.level.ServerBossEvent bossEvent =
            (net.minecraft.server.level.ServerBossEvent) new net.minecraft.server.level.ServerBossEvent(
                    this.getDisplayName(),
                    net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                    net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS)
                    .setDarkenScreen(false);

    // ---- 战斗状态 ----
    private int fightState = ST_IDLE;
    private int meleeHits = 0;                 // 累计近战命中（造成伤害）
    private int cameraCooldown = 0;            // camera_shoot 后的冷却，防止连发
    /** 中距原地 spike 冷却：触发一轮后让 Boss 先贴近/拉开，避免在 3~9 区间原地无限放 */
    private int spikeMidCooldown = 0;

    // ---- 近战进行中 ----
    private String meleeAnimName;
    private int meleeStartTick;
    private int meleeWindowStartTick;
    private int meleeWindowEndTick;
    private int meleeEndTick;
    private boolean meleeDamaged;
    /** 本次近战挥击落点是否成立：动画伤害窗口内目标在攻击距离（未中=整个窗口目标都不在范围）；
     *  未命中时 30% 概率切入 spike。不受无敌帧影响。 */
    private boolean meleeLanded;

    // ---- spike 进行中 ----
    private String spikeAnimName;
    private int spikeStartTick;
    private int spikeReleaseTick;
    private int spikeEndTick;
    private int spikeShotsDone = 0;            // 已释放远程次数

    // ---- camera 进行中 ----
    private int cameraEndTick;

    // ---- 远程射线进行中 ----
    private int rayTicksLeft;
    private double rayHeadX, rayHeadY, rayHeadZ;
    private double rayDirX, rayDirY, rayDirZ;
    /** 本次射线结果：1=命中，0=未命中，-1=仍在飞行 */
    private int rayResult = -1;
    /** 最近一次 spike 射线是否命中（供 spike 命中分支决策） */
    private boolean lastSpikeHit = false;

    public Level2StaffBossEntity(EntityType<? extends Level2StaffBossEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOVING, false);
    }

    /** 服务端移动标志（供客户端动画选择 walk） */
    public boolean isServerMoving() {
        return this.entityData.get(MOVING);
    }

    // ========== 基础属性 ==========

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // 敌对目标：最近玩家
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // 移动标志 = 导航有未完成路径（供客户端决定播 walk）
        var path = this.getNavigation().getPath();
        boolean moving = path != null && !path.isDone() && !this.getNavigation().isDone();
        if (moving != this.entityData.get(MOVING)) {
            this.entityData.set(MOVING, moving);
        }

        // 血条跟随显示（不随死亡继续显示）
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
        this.bossEvent.setVisible(this.isAlive() && !this.isDeadOrDying());

        LivingEntity target = getTarget();
        // 无目标：状态归位
        if (!(target instanceof Player player) || !player.isAlive() || player.isSpectator()) {
            resetToIdle();
            return;
        }

        // 相机冷却递减 / 中距 spike 冷却递减
        if (cameraCooldown > 0) cameraCooldown--;
        if (spikeMidCooldown > 0) spikeMidCooldown--;

        tickRay(player);
        runFightLoop(player);

        // 攻击动作期间强制面向玩家（hit/spike/camera），走路/撤退不强制
        if (isFacingState()) {
            faceTarget(player);
        }
    }

    /** 攻击动作状态：播放命中/远程动画时必须面向玩家 */
    private boolean isFacingState() {
        return fightState == ST_MELEE || fightState == ST_SPIKE || fightState == ST_CAMERA;
    }

    /** 平滑转向玩家（身体 + 头部同步）；动作状态（hit/spike/camera）要求实时面朝，转向快 */
    private void faceTarget(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        float yaw = (float) (net.minecraft.util.Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;

        // 高转向速度（90°/tick）：动作期间几乎即时对准玩家
        float yBody = this.rotateTowards(this.yBodyRot, yaw, 90.0F);
        // 同步上一帧角度，避免客户端渲染从旧角插值产生滞后/抽搐
        this.yBodyRotO = yBody;
        this.yRotO = yBody;
        this.yHeadRotO = yBody;
        this.yBodyRot = yBody;
        this.setYRot(yBody);
        this.yHeadRot = yBody;
        if (this.getNavigation().getPath() != null && !this.getNavigation().getPath().isDone()) {
            this.getNavigation().stop();
        }
    }

    /** 角度插值逼近（每 tick 最多转 speed 度） */
    private float rotateTowards(float current, float target, float speed) {
        float delta = net.minecraft.util.Mth.degreesDifference(current, target);
        if (Math.abs(delta) > speed) {
            return current + Math.copySign(speed, delta);
        }
        return current + delta;
    }

    /** 玩家进入跟踪范围 → 显示血条 */
    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    /** 玩家离开跟踪范围 → 隐藏血条 */
    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    /** 实体移除时清空血条玩家（防残留） */
    @Override
    public void remove(net.minecraft.world.entity.Entity.RemovalReason reason) {
        if (!level().isClientSide) this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    // ========== 战斗状态机 ==========

    private void runFightLoop(Player target) {
        double dist = horizontalDist(target);

        switch (fightState) {
            case ST_IDLE, ST_APPROACH -> {
                // 目标过远 → camera_shoot 骚扰一次（带冷却，防止循环）
                if (dist >= CAMERA_RANGE && cameraCooldown <= 0 && meleeHits < MELEE_HITS_TO_RETREAT) {
                    startCamera();
                    return;
                }
                // 近战累计达标 → 撤退（仅当仍贴身时；若已被拉远则直接视作已撤退到位）
                if (meleeHits >= MELEE_HITS_TO_RETREAT) {
                    if (dist < RETREAT_MIN) {
                        startRetreat();
                    } else {
                        startSpike();
                    }
                    return;
                }
                // 中距（3 ≤ dist < 9）：不贴近，原地放 spike 压制；
                // 带冷却防循环，且独立于「近战打满 5 次才拉远」的撤退路径。
                if (dist >= SPIKE_MID_MIN && dist < CAMERA_RANGE && spikeMidCooldown <= 0) {
                    spikeMidCooldown = 80;
                    startSpike();
                    return;
                }
                // 交互距离内 → 近战
                if (dist <= MELEE_RANGE + target.getBbWidth() * 0.5D) {
                    startMelee(target);
                    return;
                }
                // 否则向玩家靠近
                fightState = ST_APPROACH;
                this.getNavigation().moveTo(target, 1.0D);
            }
            case ST_MELEE -> {
                int elapsed = this.tickCount - meleeStartTick;
                // 窗口内 + 目标仍在交互距离 → 挥击落点成立：施放伤害（无敌帧吞伤不计入"未命中"）
                if (!meleeDamaged && elapsed >= meleeWindowStartTick && elapsed <= meleeWindowEndTick) {
                    double d = horizontalDist(target);
                    if (d <= MELEE_RANGE + target.getBbWidth() * 0.5D) {
                        meleeDamaged = true;
                        meleeLanded = true; // 挥击命中成立（动画窗口内目标在范围）
                        playBossSound(soundForMelee(meleeAnimName));
                        if (this.doHurtTarget(target)) {
                            meleeHits++;
                        }
                    }
                }
                // 动画播完：撤退 / 近战未命中 30% 切入 spike / 连击 / 追击
                if (this.tickCount >= meleeEndTick) {
                    double d = horizontalDist(target);
                    boolean stillClose = d <= MELEE_RANGE + target.getBbWidth() * 0.5D;
                    if (meleeHits >= MELEE_HITS_TO_RETREAT) {
                        if (d < RETREAT_MIN) {
                            startRetreat();
                        } else {
                            startSpike();
                        }
                    } else if (!meleeLanded && this.random.nextFloat() < 0.30f) {
                        // 本次挥击未命中（目标躲开/伤害被吞）→ 30% 原地切入 spike（无需先拉远），
                        // 后续命中分支与远程 spike 一致（一/二发命中→camera，未命中→下一发/近身）。
                        startSpike();
                    } else if (stillClose) {
                        startMelee(target);   // 无缝衔接下一个随机 hit
                    } else {
                        fightState = ST_IDLE; // 目标已跑远 → 追击
                    }
                }
            }
            case ST_RETREAT -> {
                // 远离玩家
                if (dist < RETREAT_MIN) {
                    this.getNavigation().stop();
                    Vec3 away = this.position().subtract(target.position()).normalize();
                    double want = RETREAT_MIN + 1.5D;
                    double px = target.getX() + away.x * want;
                    double pz = target.getZ() + away.z * want;
                    this.getNavigation().moveTo(px, this.getY(), pz, 1.1D);
                } else if (dist <= RETREAT_MAX) {
                    this.getNavigation().stop();
                    startSpike();
                } else {
                    // 撤退过头 → 向目标微调回 6~9 区间
                    this.getNavigation().stop();
                    Vec3 toward = target.position().subtract(this.position()).normalize();
                    double want = RETREAT_MAX - 1.0D;
                    double px = this.getX() + toward.x * (dist - want);
                    double pz = this.getZ() + toward.z * (dist - want);
                    this.getNavigation().moveTo(px, this.getY(), pz, 1.0D);
                }
            }
            case ST_SPIKE -> {
                // 到达释放点 → 释放远程（黑烟射线 + 音效）并累计释放次数
                if (this.tickCount == spikeReleaseTick) {
                    spikeShotsDone++;
                    fireSmokeRay(target);
                    playBossSound(ModSounds.LEVEL2_STAFF_SPIKE_HIT.get());
                }
                // 等动画播完且射线已落地，才据命中结果分支
                if (this.tickCount >= spikeEndTick && this.rayTicksLeft <= 0) {
                    boolean hit = this.lastSpikeHit;
                    if (spikeShotsDone == 1) {
                        // 第一次 spike：命中 → camera_shoot；未命中 → 第二次 spike
                        if (hit) {
                            spikeShotsDone = 0;
                            startCamera();
                        } else {
                            startSpike();
                        }
                    } else {
                        // 第二次 spike：命中 → camera_shoot；未命中 → 近身
                        meleeHits = 0;
                        spikeShotsDone = 0;
                        if (hit) {
                            startCamera();
                        } else {
                            fightState = ST_IDLE;
                        }
                    }
                }
            }
            case ST_CAMERA -> {
                // 动画放完 → 施加 debuff 并必然接近玩家（不再进入 spike）
                if (this.tickCount >= cameraEndTick) {
                    applyCameraDebuff();
                    cameraCooldown = 60; // 冷却防止对远处目标无限连发
                    meleeHits = 0;
                    fightState = ST_APPROACH;
                    this.getNavigation().moveTo(target, 1.0D);
                }
            }
            default -> fightState = ST_IDLE;
        }
    }

    /** 开始一次近战（随机 hit1/hit2/hit3），记录窗口/时长 */
    private void startMelee(Player target) {
        MeleeAnim anim = Level2StaffBossAnimations.randomMelee(this.random);
        this.getNavigation().stop();

        this.fightState = ST_MELEE;
        this.meleeAnimName = anim.name();
        this.meleeStartTick = this.tickCount;
        this.meleeWindowStartTick = Math.round(anim.startSec() * 20f);
        this.meleeWindowEndTick = Math.round(anim.endSec() * 20f);
        // 动画播完（meleeEndTick）同一 tick 才允许衔接下一段，绝不在播完前提前触发
        this.meleeEndTick = Math.round(anim.lengthSec() * 20f) + this.tickCount;
        this.meleeDamaged = false;
        this.meleeLanded = false;

        triggerAnim(ANIM_CONTROLLER, anim.name());
    }

    /** 开始撤退 */
    private void startRetreat() {
        this.fightState = ST_RETREAT;
        this.getNavigation().stop();
    }

    /** 开始一次 spike（随机 spike_hit/spike_hit2），记录释放点 */
    private void startSpike() {
        SpikeAnim anim = Level2StaffBossAnimations.randomSpike(this.random);
        this.getNavigation().stop();

        this.fightState = ST_SPIKE;
        this.spikeAnimName = anim.name();
        this.spikeStartTick = this.tickCount;
        this.spikeReleaseTick = this.tickCount + Math.round(anim.releaseSec() * 20f);
        // +1 tick：确保客户端动画完整播完再放下一段，避免掐断
        this.spikeEndTick = this.tickCount + Math.round(anim.lengthSec() * 20f) + 1;

        triggerAnim(ANIM_CONTROLLER, anim.name());
    }

    /** 开始 camera_shoot（动画起即播音效；debuff 在动画放完后由 ST_CAMERA 分支施加） */
    private void startCamera() {
        this.getNavigation().stop();
        this.fightState = ST_CAMERA;
        this.cameraEndTick = this.tickCount
                + Math.round(Level2StaffBossAnimations.CAMERA_SHOOT_LENGTH_SEC * 20f);
        triggerAnim(ANIM_CONTROLLER, Level2StaffBossAnimations.CAMERA_SHOOT);
        playBossSound(ModSounds.LEVEL2_STAFF_CAMERA_SHOOT.get());
    }

    /** camera_shoot：以自身为中心 32 格内所有玩家获得迟缓/挖掘疲劳/虚弱 255 级，持续 15s */
    private void applyCameraDebuff() {
        if (level().isClientSide) return;
        int duration = 15 * 20; // 15 秒
        int amp = 254;
        var effects = java.util.List.of(
                net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN,
                net.minecraft.world.effect.MobEffects.WEAKNESS);
        for (net.minecraft.server.level.ServerPlayer p :
                level().getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                        this.getBoundingBox().inflate(32.0D))) {
            for (var e : effects) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(e, duration, amp, false, true, true));
            }
        }
    }

    private void resetToIdle() {
        this.fightState = ST_IDLE;
        this.meleeHits = 0;
        this.spikeShotsDone = 0;
        this.spikeMidCooldown = 0;
        this.rayTicksLeft = 0;
        this.lastSpikeHit = false;
        this.rayResult = -1;
        this.getNavigation().stop();
    }

    // ========== 远程射线（黑烟粒子，命中 10 点魔法伤害） ==========

    /** 从自身中心向目标释放一道黑烟粒子射线；命中结果异步由 {@link #tickRay} 写入 {@code lastSpikeHit} */
    private void fireSmokeRay(Player target) {
        Vec3 origin = this.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(origin);
        double range = toTarget.length();
        Vec3 dir = toTarget.normalize();

        this.rayHeadX = origin.x;
        this.rayHeadY = origin.y;
        this.rayHeadZ = origin.z;
        this.rayDirX = dir.x;
        this.rayDirY = dir.y;
        this.rayDirZ = dir.z;
        this.rayTicksLeft = Math.max(1, (int) Math.ceil(range / RAY_SPEED));
        this.rayResult = -1;   // 飞行中
    }

    /** 每 tick 推进射线，撒黑烟粒子，命中玩家造成 10 点魔法伤害 */
    private void tickRay(Player target) {
        if (rayTicksLeft <= 0) return;
        rayTicksLeft--;

        // 推进
        rayHeadX += rayDirX * RAY_SPEED;
        rayHeadY += rayDirY * RAY_SPEED;
        rayHeadZ += rayDirZ * RAY_SPEED;

        // 撒黑烟粒子（客户端广播）
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    rayHeadX, rayHeadY, rayHeadZ,
                    6, 0.12D, 0.12D, 0.12D, 0.02D);
        }

        // 命中判定：头部接近目标碰撞箱
        if (target.isAlive()) {
            double dx = rayHeadX - target.getX();
            double dy = rayHeadY - target.getY() - target.getBbHeight() * 0.5D;
            double dz = rayHeadZ - target.getZ();
            double hitRadius = 0.9D + target.getBbWidth() * 0.5D;
            if (dx * dx + dy * dy + dz * dz < hitRadius * hitRadius) {
                rayTicksLeft = 0;
                rayResult = 1;
                target.hurt(this.damageSources().indirectMagic(this, this), RAY_DAMAGE);
            }
        }
        if (rayTicksLeft <= 0) {
            // 飞行结束（无论命中与否）记录结果
            lastSpikeHit = rayResult == 1;
            rayHeadX = rayHeadY = rayHeadZ = 0;
        }
    }

    // ========== 音效 ==========

    private SoundEvent soundForMelee(String animName) {
        return switch (animName) {
            case "hit1" -> ModSounds.LEVEL2_STAFF_HIT1.get();
            case "hit2" -> ModSounds.LEVEL2_STAFF_HIT2.get();
            case "hit3" -> ModSounds.LEVEL2_STAFF_HIT3.get();
            default -> ModSounds.LEVEL2_STAFF_HIT1.get();
        };
    }

    /** 在实体位置广播音效，响度/音高随机浮动 ±10% */
    private void playBossSound(SoundEvent sound) {
        float vol = 1.0f + (this.random.nextFloat() - 0.5f) * 0.2f;
        float pitch = 1.0f + (this.random.nextFloat() - 0.5f) * 0.2f;
        this.level().playSound(null,
                this.getX(), this.getEyeY(), this.getZ(),
                sound, SoundSource.HOSTILE, vol, pitch);
    }

    // ========== 工具 ==========

    private double horizontalDist(Player target) {
        double dx = this.getX() - target.getX();
        double dz = this.getZ() - target.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ========== 动画 ==========
    // Ars Nouveau 模式：动作（hit/spike/camera）与移动（walk）分两个 controller。
    // 动作由 triggerAnim 在独立 action controller 上完整播放；移动 controller 只按 MOVING 播 walk。
    // 动作进行时服务端已停导航（MOVING=false），move controller STOP，二者不互相打断/抢骨骼。

    /** 动作动画 controller 名（triggerAnim 目标） */
    private static final String ANIM_CONTROLLER = "action_controller";

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 移动 controller：MOVING → walk 循环
        AnimationController<Level2StaffBossEntity> moveController =
                new AnimationController<>(this, "move_controller", 0, this::movePredicate);
        controllers.add(moveController);

        // 动作 controller：predicate 不主动播（恒 STOP，等待 triggerAnim），trigger 动画一次播放
        AnimationController<Level2StaffBossEntity> actionController =
                new AnimationController<>(this, ANIM_CONTROLLER, 0, this::actionPredicate);
        for (String animName : Level2StaffBossAnimations.ATTACK_NAMES) {
            actionController.triggerableAnim(animName, RawAnimation.begin().thenPlay(animName));
        }
        controllers.add(actionController);
    }

    /** 移动：MOVING 标志（服务端导航）为真 → 播 walk；否则 STOP（不干扰动作） */
    private <E extends Level2StaffBossEntity> PlayState movePredicate(AnimationState<E> state) {
        if (this.isServerMoving()) {
            state.setAnimation(Level2StaffBossAnimations.WALK);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    /** 动作：只播 triggerAnim 触发的一次性动画；无触发时不播任何动作 */
    private <E extends Level2StaffBossEntity> PlayState actionPredicate(AnimationState<E> state) {
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    @Override
    public double getTick(Object entity) {
        return this.tickCount;
    }
}
