package com.plumejade.lensouls.boss;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * BOSS 韧性数据结构（服务端）。
 * <p>
 * 每个 BOSS 实体持有一个实例，记录当前已被削韧值、韧性上限、破防状态等。
 * 实现 {@link INBTSerializable} 以便通过 NeoForge Attachment 随实体持久化。
 */
public class BossToughnessData implements INBTSerializable<CompoundTag> {

    /** 需要的总削韧次数（由 BOSS 血量换算） */
    private int requiredHits;
    /** 当前已被削韧次数 */
    private int currentHits;
    /** 是否已破防（韧性条被打空） */
    private boolean broken;
    /** 破防剩余的 tick（0 = 不在破防状态） */
    private int stunRemainingTicks;
    /** 定身期间玩家累计造成的伤害（用于伤害达标提前解除定身） */
    private float stunAccumulatedDamage;

    /** 韧性恢复倒计时（tick），削韧后开始倒计时，到 0 恢复 */
    private int recoveryTicks;
    /** 总恢复时长（tick），用于进度计算 */
    private int maxRecoveryTicks;
    /** 削韧无敌窗口（3秒 = 60 tick），每次 hit 后重置 */
    private int invincibleTicks;

    public BossToughnessData() {}

    public BossToughnessData(int requiredHits) {
        this.requiredHits = requiredHits;
        this.currentHits = 0;
        this.broken = false;
        this.stunRemainingTicks = 0;
        this.stunAccumulatedDamage = 0;
        this.recoveryTicks = 0;
        this.maxRecoveryTicks = 0;
        this.invincibleTicks = 0;
    }

    // ========== Getters ==========

    /** 当前韧性进度 [0..1]，0=满韧性，1=破防 */
    public float getProgress() {
        if (requiredHits <= 0) return 0;
        return Math.min(1.0f, (float) currentHits / requiredHits);
    }

    public int getRequiredHits() { return requiredHits; }
    public int getCurrentHits() { return currentHits; }
    public boolean isBroken() { return broken; }
    public int getStunRemainingTicks() { return stunRemainingTicks; }
    public float getStunAccumulatedDamage() { return stunAccumulatedDamage; }

    /** 定身期间累计玩家伤害，返回累计值 */
    public float addStunDamage(float amount) {
        this.stunAccumulatedDamage += amount;
        return this.stunAccumulatedDamage;
    }
    public int getRecoveryTicks() { return recoveryTicks; }
    public int getMaxRecoveryTicks() { return maxRecoveryTicks; }

    /** 是否处于削韧无敌窗口 */
    public boolean isInvincible() { return invincibleTicks > 0; }
    public void setInvincibleTicks(int ticks) { this.invincibleTicks = Math.max(0, ticks); }

    /** 减伤比例 [0..1]: 满韧性时 = config 值，破防后 = 0 */
    public float getDamageReduction(float maxReduction) {
        if (broken) return 0;
        float progress = getProgress();
        // progress=0 → 满减伤, progress=1 → 无减伤
        return maxReduction * (1.0f - progress);
    }

    // ========== 削韧 ==========

    /**
     * 削韧一击。
     * @param invincibleTicks 本次削韧后的无敌窗口（由调用方根据 BOSS 属性传入）
     * @return true = 成功削韧；false = 被无敌或破刹状态阻挡
     */
    public boolean hit(int invincibleTicks) {
        return hit(invincibleTicks, 1.0f);
    }

    /**
     * 削韧一击（带倍率）。
     * @param invincibleTicks 本次削韧后的无敌窗口
     * @param hitMultiplier 每次削韧的倍率（奖杯修饰符）
     */
    public boolean hit(int invincibleTicks, float hitMultiplier) {
        if (broken) return false;
        if (requiredHits <= 0) return false;
        if (this.invincibleTicks > 0) return false;

        currentHits = Math.min(currentHits + (int) hitMultiplier, requiredHits);
        this.invincibleTicks = Math.max(1, invincibleTicks);

        recoveryTicks = maxRecoveryTicks;

        if (currentHits >= requiredHits) {
            broken = true;
            stunRemainingTicks = -1;
        }
        return true;
    }

    /** 兼容旧调用（默认 60 tick） */
    public boolean hit() {
        return hit(60);
    }

    /** 调用方在确认破防后设置定身持续时间（每次破韧重新累计伤害） */
    public void setStunTicks(int ticks) {
        this.stunRemainingTicks = ticks;
        this.stunAccumulatedDamage = 0;
    }

    /** 每 tick 递减恢复/无敌倒计时 */
    public void tick() {
        if (broken) {
            if (stunRemainingTicks > 0) {
                stunRemainingTicks--;
            }
            return;
        }
        if (invincibleTicks > 0) {
            invincibleTicks--;
        }
        if (recoveryTicks > 0) {
            recoveryTicks--;
            if (recoveryTicks <= 0) {
                reset();
            }
        }
    }

    /** 重置韧性（同时清除无敌窗口与定身累计伤害） */
    public void reset() {
        this.currentHits = 0;
        this.broken = false;
        this.stunRemainingTicks = 0;
        this.stunAccumulatedDamage = 0;
        this.recoveryTicks = 0;
        this.invincibleTicks = 0;
    }

    /** 破防结束时调用（由调用方决定何时结束） */
    public void onStunEnd() {
        this.stunRemainingTicks = 0;
        reset();
    }

    /** 初始化恢复时长（基于配置的秒数） */
    public void setRecoveryFromSeconds(int seconds) {
        this.maxRecoveryTicks = seconds * 20;
        this.recoveryTicks = maxRecoveryTicks;
    }

    // ========== NBT ==========

    private static final String TAG_REQUIRED = "requiredHits";
    private static final String TAG_CURRENT = "currentHits";
    private static final String TAG_BROKEN = "broken";
    private static final String TAG_STUN = "stunTicks";
    private static final String TAG_STUN_ACCUMULATED = "stunAccumulatedDamage";
    private static final String TAG_RECOVERY = "recoveryTicks";
    private static final String TAG_MAX_RECOVERY = "maxRecoveryTicks";
    private static final String TAG_INVINCIBLE = "invincibleTicks";

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider provider) {
        return serialize();
    }

    @Override
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider provider, CompoundTag tag) {
        readFromNBT(tag);
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_REQUIRED, requiredHits);
        tag.putInt(TAG_CURRENT, currentHits);
        tag.putBoolean(TAG_BROKEN, broken);
        tag.putInt(TAG_STUN, stunRemainingTicks);
        tag.putFloat(TAG_STUN_ACCUMULATED, stunAccumulatedDamage);
        tag.putInt(TAG_RECOVERY, recoveryTicks);
        tag.putInt(TAG_MAX_RECOVERY, maxRecoveryTicks);
        tag.putInt(TAG_INVINCIBLE, invincibleTicks);
        return tag;
    }

    /** 将 NBT 数据填充到本实例（Attachment 反序列化用） */
    public void readFromNBT(CompoundTag tag) {
        this.requiredHits = tag.getInt(TAG_REQUIRED);
        this.currentHits = tag.getInt(TAG_CURRENT);
        this.broken = tag.getBoolean(TAG_BROKEN);
        this.stunRemainingTicks = tag.getInt(TAG_STUN);
        this.stunAccumulatedDamage = tag.getFloat(TAG_STUN_ACCUMULATED);
        this.recoveryTicks = tag.getInt(TAG_RECOVERY);
        this.maxRecoveryTicks = tag.getInt(TAG_MAX_RECOVERY);
        this.invincibleTicks = tag.getInt(TAG_INVINCIBLE);
    }

    public static BossToughnessData deserialize(CompoundTag tag) {
        BossToughnessData data = new BossToughnessData();
        data.readFromNBT(tag);
        return data;
    }
}
