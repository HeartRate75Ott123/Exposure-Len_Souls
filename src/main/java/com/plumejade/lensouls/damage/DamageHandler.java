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
        Level level = target.level();
        float originalDamage = event.getOriginalDamage();
        if (originalDamage <= 0f) return;

        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        float totalBonusMultiplier = 0f;
        java.util.Set<ElementDamage> activeElements = java.util.EnumSet.noneOf(ElementDamage.class);

        // 1-2. 玩家活性：灌注 + 独立武器
        if (source.getEntity() instanceof Player player) {
            boolean slowness = ElementInfusionEffect.hasPlayerSlowness(player);
            ResourceLocation weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());

            for (MobEffectInstance inst : player.getActiveEffects()) {
                if (!(inst.getEffect().value() instanceof ElementInfusionEffect effect)) continue;
                ElementDamage element = effect.getElement();
                activeElements.add(element);

                float weakness = DataPackLoader.getWeakness(entityId, element);
                float potionActivity = ElementDamage.getActivityByAmplifier(inst.getAmplifier());
                float weaponActivity = ItemElementActivityLoader.getActivity(weaponId, element);

                if (weakness > 0f && (potionActivity > 0f || weaponActivity > 0f)) {
                    totalBonusMultiplier += (weaponActivity + potionActivity) * weakness;
                    emitSpiralParticle(level, target, element);
                }

                if (slowness && element == ElementDamage.WATER) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
                }

                BossPhantomType bossType = BossPhantomType.fromSoulItem(
                        ElementDamage.getActivityByAmplifier(inst.getAmplifier()), slowness, element);
                if (bossType != null) sendHitParticles((ServerLevel) level, target, bossType);
            }

            for (ElementDamage element : ElementDamage.values()) {
                if (element == ElementDamage.PROJECTILE || activeElements.contains(element)) continue;
                float wActivity = ItemElementActivityLoader.getActivity(weaponId, element);
                if (wActivity <= 0f) continue;
                float weakness = DataPackLoader.getWeakness(entityId, element);
                if (weakness > 0f) {
                    totalBonusMultiplier += wActivity * weakness;
                    emitSpiralParticle(level, target, element);
                }
                activeElements.add(element);
            }
        }

        // 2b. 次元枪子弹固有元素：弹药类型→元素映射（活性=2.0，对应镜魂3级）
        if (source.getDirectEntity() instanceof com.plumejade.lensouls.entity.GunBulletEntity gunBullet) {
            ElementDamage bulletElement = com.plumejade.lensouls.entity.GunBulletEntity.getBulletElement(gunBullet.getBulletType());
            if (bulletElement != null) {
                float w = DataPackLoader.getWeakness(entityId, bulletElement);
                if (w > 0f) {
                    totalBonusMultiplier += 2.0f * w;
                    emitSpiralParticle(level, target, bulletElement);
                }
            }
        }

        // 3. 弹射物
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            float projWeakness = DataPackLoader.getWeakness(entityId, ElementDamage.PROJECTILE);
            totalBonusMultiplier += projWeakness;
            if (projWeakness > 0f) emitSpiralParticle(level, target, ElementDamage.PROJECTILE);
        }

        // 4. 伤害类型 → 元素映射
        ResourceLocation dtId = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getKey(source.type());
        if (dtId != null && DamageTypeElementLoader.hasMapping(dtId)) {
            ElementDamage dtElement = DamageTypeElementLoader.getElement(dtId);
            float dtActivity = DamageTypeElementLoader.getActivity(dtId, dtElement);
            if (source.getEntity() instanceof Player && !activeElements.contains(dtElement)) dtActivity = 0f;
            if (dtActivity > 0f) {
                float dtWeakness = DataPackLoader.getWeakness(entityId, dtElement);
                if (dtWeakness > 0f) {
                    totalBonusMultiplier += dtActivity * dtWeakness;
                    emitSpiralParticle(level, target, dtElement);
                }
            }
        }

        // 5. 攻击者实体 → 元素映射
        if (source.getEntity() != null) {
            ResourceLocation attackerId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntity().getType());
            if (AttackerElementLoader.hasMapping(attackerId)) {
                ElementDamage atkElement = AttackerElementLoader.getElement(attackerId);
                float atkActivity = AttackerElementLoader.getActivity(attackerId, atkElement);
                if (source.getEntity() instanceof Player && !activeElements.contains(atkElement)) atkActivity = 0f;
                // 玩家被攻击时：若自身有对应元素灌注，免疫该元素追加伤害（灌注防护）
                if (target instanceof Player targetPlayer) {
                    for (var inst : targetPlayer.getActiveEffects()) {
                        if (inst.getEffect().value() instanceof ElementInfusionEffect eif
                                && eif.getElement() == atkElement) {
                            atkActivity = 0f;
                            break;
                        }
                    }
                }
                if (atkActivity > 0f) {
                    float atkWeakness = DataPackLoader.getWeakness(entityId, atkElement);
                    if (atkWeakness > 0f) {
                        totalBonusMultiplier += atkActivity * atkWeakness;
                        emitSpiralParticle(level, target, atkElement);
                    }
                }
            }
        }

        if (!level.isClientSide && totalBonusMultiplier > 0f) {
            event.setNewDamage(originalDamage + originalDamage * totalBonusMultiplier);
        }
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
