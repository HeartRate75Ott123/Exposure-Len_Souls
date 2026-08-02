package com.plumejade.lensouls.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 扭曲者：玩家扭曲值满 100 死亡时生成的惩罚性怪物。
 * <p>
 * 继承僵尸模型/贴图模板，但目标 AI 完全替换——只仇恨并攻击归属玩家（owner）。
 * 属性由生成时玩家状态决定：最大生命 = 玩家死亡时最大生命，攻击力 = 最大生命/5×2，护甲 5。
 */
public class TwitcherEntity extends Zombie {

    public static final String KEY_OWNER = "lensouls:twitcher_owner";

    public TwitcherEntity(EntityType<? extends TwitcherEntity> type, Level level) {
        super(type, level);
    }

    /** 归属玩家（生成时写入，持久保存） */
    public void setOwner(UUID owner) {
        this.getPersistentData().putUUID(KEY_OWNER, owner);
    }

    public UUID getOwnerUuid() {
        if (!this.getPersistentData().contains(KEY_OWNER)) return null;
        return this.getPersistentData().getUUID(KEY_OWNER);
    }

    public boolean isOwnedBy(UUID uuid) {
        return uuid != null && uuid.equals(getOwnerUuid());
    }

    /** 完全替换原版僵尸 AI：只攻击归属玩家 */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new TwitchOwnerTargetGoal());
    }

    /** 每 tick 将目标锁定为归属玩家（重生后继续攻击，远离则放弃回出生点游荡） */
    private class TwitchOwnerTargetGoal extends Goal {

        @Override
        public boolean canUse() {
            return getOwnerUuid() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            UUID ownerId = getOwnerUuid();
            if (ownerId == null) {
                setTarget(null);
                return;
            }
            Player owner = level().getPlayerByUUID(ownerId);
            if (owner != null && owner.isAlive() && !owner.isSpectator()) {
                if (getTarget() != owner) {
                    setTarget(owner);
                }
            } else {
                setTarget(null);
            }
        }
    }

    /** 生成时按玩家状态配置属性（最大生命/攻击力/护甲/命名防刷除） */
    public void initFromPlayer(Player player) {
        double maxHp = player.getMaxHealth();
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHp);
        this.setHealth((float) maxHp);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(maxHp / 5 * 2);
        this.getAttribute(Attributes.ARMOR).setBaseValue(5.0);
        this.setCustomName(Component.literal("扭曲者"));
        this.setCustomNameVisible(false);
    }

    /** 有自定义名称 → 不会因玩家远离而被自然刷除（命名牌机制） */
    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    /** 不会被太阳点燃（扭曲者是惩罚实体，不应被环境机制消除） */
    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return getCustomName() != null;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    /** 无任何掉落（连僵尸腐肉也没有，防刷循环） */
    @Override
    protected boolean shouldDropLoot() {
        return false;
    }
}
