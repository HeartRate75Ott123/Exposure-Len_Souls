package com.plumejade.lensouls.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * BOSS 虚影幻灵实体——纯视觉效果。
 * <p>
 * 动画对象以 {@link Object} 存储（运行时为 LionfishAPI 的 Animation，仅在有灾变/lionfishapi 时存在），
 * 避免无联动 mod 时服务端实体注册加载失败。
 */
public class BossPhantomEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_BOSS_TYPE =
            SynchedEntityData.defineId(BossPhantomEntity.class, EntityDataSerializers.INT);

    /** 渲染/动画阶段：0=IDLE, 1=CHARGE, 2=EXECUTE, 3=DECAY */
    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(BossPhantomEntity.class, EntityDataSerializers.INT);

    private UUID ownerUuid;
    private Object animation;
    private int animationTick;

    public BossPhantomEntity(EntityType<? extends BossPhantomEntity> type, Level level) {
        super(type, level);
    }

    public BossPhantomEntity(Level level, BossPhantomType bossType, UUID ownerUuid,
                             double x, double y, double z, float yaw) {
        super(ModEntities.BOSS_PHANTOM.get(), level);
        this.ownerUuid = ownerUuid;
        this.entityData.set(DATA_BOSS_TYPE, bossType.ordinal());
        setPos(x, y, z);
        setYRot(yaw);
        setOldPosAndRot();
    }

    // ========== 动画（Object 承载，运行时为 LionfishAPI Animation） ==========

    public int getAnimationTick() { return animationTick; }

    public void setAnimationTick(int tick) { this.animationTick = tick; }

    public Object getAnimation() { return animation; }

    public void setAnimation(Object anim) {
        this.animation = anim;
        this.animationTick = 0;
    }

    /** BossPhantomManager 在启动幻灵时调用此方法设置技能动画 */
    public void startAnimation(Object anim) {
        setAnimation(anim);
    }

    // ========== 数据同步 ==========

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_BOSS_TYPE, 0);
        builder.define(DATA_PHASE, 0);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("BossPhantomType")) {
            entityData.set(DATA_BOSS_TYPE, tag.getInt("BossPhantomType"));
        }
        if (tag.hasUUID("OwnerUUID")) {
            ownerUuid = tag.getUUID("OwnerUUID");
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("BossPhantomType", entityData.get(DATA_BOSS_TYPE));
        if (ownerUuid != null) {
            tag.putUUID("OwnerUUID", ownerUuid);
        }
    }

    // ========== 属性 ==========

    @Override public boolean isPickable()                { return false; }
    @Override public boolean isPushable()                { return false; }
    @Override public boolean isNoGravity()               { return true; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return true; }

    public int getBossTypeOrdinal() { return entityData.get(DATA_BOSS_TYPE); }

    public BossPhantomType getBossType() {
        int ord = getBossTypeOrdinal();
        if (ord < 0 || ord >= BossPhantomType.values().length) return BossPhantomType.IGNIS;
        return BossPhantomType.values()[ord];
    }

    // ========== 阶段属性 ==========

    /** 幻灵静止/idle 阶段 */
    public static final int PHASE_IDLE = 0;
    /** 蓄力——亮度递增、上浮旋转 */
    public static final int PHASE_CHARGE = 1;
    /** 爆发——技能动画 + 全白闪烁 */
    public static final int PHASE_EXECUTE = 2;
    /** 余辉——粒子衰减 + alpha 渐隐 */
    public static final int PHASE_DECAY = 3;

    /** 设置渲染/动画阶段（服务端 → 自动同步客户端） */
    public void setPhantomPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    /** 获取当前渲染/动画阶段 */
    public int getPhantomPhase() {
        return entityData.get(DATA_PHASE);
    }

    // ========== Tick ==========

    @Override
    public void tick() {
        super.tick();
        // 动画进度推进（原 getDuration 封顶逻辑依赖 lionfishapi，已移除；渲染端按 keyframe 总长自然结束）
        if (animation != null) {
            animationTick++;
        }
    }
}
