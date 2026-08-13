package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.DamageTypeElementLoader;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.entity.BossPhantomType;
import com.plumejade.lensouls.network.ElementSpiralPacket;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 元素追加伤害处理器（统一公式）。
 * <p>
 * 对所有元素统一计算：
 * <pre>
 * 追加伤害 = 护甲后伤害 × 受击者弱点 × Σ(攻击方活性)
 * Σ(攻击方活性) = 武器活性 + 药水活性 + 实体活性 + 子弹活性 + 伤害类型活性
 * </pre>
 * 活性来源按等级制（0~9，0.5 步进）：等级 1 = 1.0，每级 +0.5，见 {@link ElementDamage#getActivityByLevel}。
 * <p>
 * 免伤对抗（对玩家打实体、实体打玩家对称适用）：
 * 受击方身上对应元素的灌注药水等级 ≥ 攻击方该元素活性等级 → 该元素追加完全免疫（原伤害照常）。
 * 攻击方活性等级：玩家 = 武器活性等级，实体 = attacker_element 配置等级。
 */
public class DamageHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        Level level = target.level();
        float originalDamage = event.getOriginalDamage();
        if (originalDamage <= 0f) return;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        float totalBonusMultiplier = 0f;

        Entity attacker = source.getEntity();
        boolean isPlayer = attacker instanceof Player;
        Player player = isPlayer ? (Player) attacker : null;

        // 攻击者侧基础信息
        ResourceLocation weaponId = isPlayer
                ? BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()) : null;
        ResourceLocation attackerId = attacker != null
                ? BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType()) : null;

        // 次元枪子弹固有元素（模组内设计，活性固定 2.0）
        ElementDamage bulletElement = null;
        if (source.getDirectEntity() instanceof com.plumejade.lensouls.entity.GunBulletEntity gunBullet) {
            bulletElement = com.plumejade.lensouls.entity.GunBulletEntity.getBulletElement(gunBullet.getBulletType());
        }

        // 伤害类型 → 元素映射（配置了才有活性，无 = 0）
        ResourceLocation dtId = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getKey(source.type());

        boolean slowness = isPlayer && ElementInfusionEffect.hasPlayerSlowness(player);

        // ── 统一元素追加：逐元素独立计算 ──
        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;

            // 攻击方活性等级（免伤对抗用）：玩家 = 武器等级，实体 = attacker_element 等级
            int attackerLevel = 0;
            float activitySum = 0f;

            if (isPlayer) {
                attackerLevel = ItemElementActivityLoader.getLevel(weaponId, element);
                activitySum += ItemElementActivityLoader.getActivity(weaponId, element);
            } else if (attacker != null) {
                attackerLevel = AttackerElementLoader.getLevel(attackerId, element);
                activitySum += AttackerElementLoader.getActivity(attackerId, element);
            }

            // 攻击者药水活性（玩家或实体均可挂灌注）
            if (attacker instanceof LivingEntity livingAttacker) {
                for (MobEffectInstance inst : livingAttacker.getActiveEffects()) {
                    if (inst.getEffect().value() instanceof ElementInfusionEffect effect
                            && effect.getElement() == element) {
                        activitySum += ElementDamage.getActivityByAmplifier(inst.getAmplifier());
                        break;
                    }
                }
            }

            // 次元枪子弹活性（模组内固定 2.0）
            if (bulletElement == element) activitySum += 2.0f;

            // 伤害类型映射活性
            if (dtId != null) {
                activitySum += DamageTypeElementLoader.getActivity(dtId, element);
            }

            // 玩家侧副作用：水灌注减速（云筑魔像镜魂）+ BOSS 镜魂命中粒子
            if (isPlayer) {
                if (slowness && element == ElementDamage.WATER) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
                }
                String soulDescId = ElementInfusionEffect.getPlayerCustomName(player, element);
                BossPhantomType bossType = soulDescId != null ? BossPhantomType.fromDescriptionId(soulDescId) : null;
                if (bossType != null) sendHitParticles((ServerLevel) level, target, bossType);
            }

            if (activitySum <= 0f) continue;

            // 免伤对抗：受击方药水等级 ≥ 攻击方等级 → 该元素追加完全免疫
            if (attackerLevel > 0 && getPotionLevel(target, element) >= attackerLevel) continue;

            float weakness = DataPackLoader.getWeakness(entityId, element);
            if (weakness > 0f) {
                totalBonusMultiplier += activitySum * weakness;
                emitSpiralParticle(level, target, element);
            }
        }

        // ── 弹射物弱点（PROJECTILE 元素，活性隐含 1.0）──
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            float projWeakness = DataPackLoader.getWeakness(entityId, ElementDamage.PROJECTILE);
            totalBonusMultiplier += projWeakness;
            if (projWeakness > 0f) emitSpiralParticle(level, target, ElementDamage.PROJECTILE);
        }

        // 追加叠加在护甲后伤害上（护甲仍然有效）
        if (!level.isClientSide && totalBonusMultiplier > 0f) {
            float current = event.getNewDamage();
            event.setNewDamage(current + current * totalBonusMultiplier);
        }
    }

    /** 获取实体身上指定元素的灌注药水等级（无灌注 = 0） */
    private static int getPotionLevel(LivingEntity entity, ElementDamage element) {
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof ElementInfusionEffect effect
                    && effect.getElement() == element) {
                return inst.getAmplifier() + 1;
            }
        }
        return 0;
    }

    /** 发射元素弱点螺旋粒子（仅显式配置的弱点） */
    private static void emitSpiralParticle(Level level, LivingEntity target, ElementDamage element) {
        if (!level.isClientSide && DataPackLoader.getAllWeaknesses(
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())).containsKey(element)) {
            PacketDistributor.sendToPlayersTrackingEntity(target,
                    new ElementSpiralPacket(target.getId(), element.ordinal(), false));
        }
    }

    // ========== 服务端命中粒子 ==========

    private static void sendHitParticles(ServerLevel level, LivingEntity target, BossPhantomType bossType) {
        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);

        switch (bossType) {
            case IGNIS -> {
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 8, 0.4, 0.3, 0.4, 0.05);
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 3, 0.3, 0.2, 0.3, 0.05);
            }
            case CLOUD_GOLEM -> {
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 10, 0.6, 0.3, 0.6, 0.08);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 4, 0.4, 0.3, 0.4, 0.05);
            }
            case POSSESSED_PALADIN -> {
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 8, 0.5, 0.4, 0.5, 0.05);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, 4, 0.3, 0.2, 0.3, 0.02);
            }
            case OBLITERATOR -> {
                level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 8, 0.8, 0.4, 0.8, 0.5);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y, pos.z, 4, 0.5, 0.3, 0.5, 0.3);
                sendModParticle(level, "legendary_monsters:green_fire_strike", pos, 6, 0.4, 0.3, 0.4, 0.3);
            }
            case ENDER_GUARDIAN -> {
                level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 8, 0.4, 0.3, 0.4, 0.05);
                level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 6, 0.5, 0.3, 0.5, 0.5);
            }
            case NETHERITE_MONSTROSITY -> {
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 8, 0.5, 0.3, 0.5, 0.05);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 6, 0.4, 0.2, 0.4, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 4, 0.3, 0.2, 0.3, 0.02);
            }
            case HYDRA -> {
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 8, 0.5, 0.3, 0.5, 0.05);
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 4, 0.3, 0.2, 0.3, 0.04);
                level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.4, 0.2, 0.4, 0.02);
            }
            case KNIGHT_PHANTOM -> {
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 6, 0.4, 0.3, 0.4, 0.04);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y, pos.z, 4, 0.4, 0.3, 0.4, 0.02);
            }
            case ALPHA_YETI -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 10, 0.6, 0.4, 0.6, 0.05);
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 5, 0.5, 0.3, 0.5, 0.04);
            }
            case NAGA -> {
                level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 8, 0.4, 0.3, 0.4, 0.3);
                level.sendParticles(ParticleTypes.ENCHANT, pos.x, pos.y, pos.z, 4, 0.3, 0.3, 0.3, 0.4);
            }
            case LAVA_EATER -> {
                level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 10, 0.5, 0.3, 0.5, 0.05);
                level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 5, 0.4, 0.2, 0.4, 0.04);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 3, 0.3, 0.2, 0.3, 0.02);
            }
            case THE_LEVIATHAN -> {
                level.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, 8, 0.6, 0.4, 0.6, 0.4);
                level.sendParticles(ParticleTypes.SOUL, pos.x, pos.y, pos.z, 4, 0.4, 0.3, 0.4, 0.03);
                level.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 4, 0.4, 0.3, 0.4, 0.03);
            }
            case SCYLLA -> {
                level.sendParticles(ParticleTypes.BUBBLE, pos.x, pos.y, pos.z, 10, 0.5, 0.3, 0.5, 0.04);
                level.sendParticles(ParticleTypes.DRIPPING_WATER, pos.x, pos.y, pos.z, 4, 0.4, 0.3, 0.4, 0.02);
            }
        }
    }

    private static void sendModParticle(ServerLevel level, String particleId, Vec3 pos,
                                        int count, double xSpread, double ySpread, double zSpread, double speed) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(particleId));
        if (type instanceof SimpleParticleType spt) {
            level.sendParticles(spt, pos.x, pos.y, pos.z, count, xSpread, ySpread, zSpread, speed);
        }
    }
}
