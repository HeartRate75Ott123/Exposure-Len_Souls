package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.LenSouls;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

/**
 * 元素伤害追加处理器。
 * <p>
 * 公式: 追加 = 原伤害 × (武器活性 + 药水活性) × 实体弱点
 * 多元素追加累加。
 */
public class DamageHandler {

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        float originalDamage = event.getOriginalDamage();
        Level level = target.level();

        if (originalDamage <= 0f) return;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        float totalBonusMultiplier = 0f;

        // 1. 玩家元素附魔检测
        if (source.getEntity() instanceof Player player) {
            boolean slowness = ElementInfusionEffect.hasPlayerSlowness(player);
            ItemStack weapon = player.getMainHandItem();
            ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(weapon.getItem());

            for (MobEffectInstance inst : player.getActiveEffects()) {
                if (inst.getEffect().value() instanceof ElementInfusionEffect elementEffect) {
                    ElementDamage element = elementEffect.getElement();

                    // 弱点倍率（无配置默认 0.1，PROJECTILE 默认 0）
                    float weakness = DataPackLoader.getWeakness(entityId, element);

                    // 药水活性（effect amplifier 决定）
                    float potionActivity = ElementDamage.getActivityByAmplifier(inst.getAmplifier());

                    // 武器活性（数据包配置）
                    float weaponActivity = ItemElementActivityLoader.getActivity(weaponId, element);

                    if (weakness > 0f && (potionActivity > 0f || weaponActivity > 0f)) {
                        totalBonusMultiplier += (weaponActivity + potionActivity) * weakness;
                    }

                    // 云筑魔像镜魂：每次近战攻击附加减速 II（3 秒）
                    if (slowness && element == ElementDamage.WATER && target != null) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
                    }

                    // 服务端：元素弱点螺旋粒子 + BOSS 命中特效
                    if (!level.isClientSide) {
                        if (weakness > 0f) {
                            PacketDistributor.sendToPlayersTrackingEntity(target,
                                    new ElementSpiralPacket(target.getId(), element.ordinal(), false));
                        }

                        BossPhantomType bossType = BossPhantomType.fromSoulItem(
                                ElementDamage.getActivityByAmplifier(inst.getAmplifier()),
                                slowness, element);
                        if (bossType != null) {
                            sendHitParticles((ServerLevel) level, target, bossType);
                        }
                    }
                }
            }
        }

        // 2. 弹射物伤害检测
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            float projWeakness = DataPackLoader.getWeakness(entityId, ElementDamage.PROJECTILE);
            if (projWeakness > 0f && !level.isClientSide) {
                PacketDistributor.sendToPlayersTrackingEntity(target,
                        new ElementSpiralPacket(target.getId(), ElementDamage.PROJECTILE.ordinal(), false));
            }
            totalBonusMultiplier += projWeakness;
        }

        // 3. 应用追加伤害（仅服务端）
        if (!level.isClientSide && totalBonusMultiplier > 0f) {
            float bonusDamage = originalDamage * totalBonusMultiplier;
            event.setNewDamage(originalDamage + bonusDamage);
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
