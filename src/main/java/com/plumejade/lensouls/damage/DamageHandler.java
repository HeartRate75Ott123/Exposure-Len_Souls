package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.DamageTypeElementLoader;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.effect.SoulDotEffect;
import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import com.plumejade.lensouls.handler.SoulDotHandler;
import com.plumejade.lensouls.integration.PhotoSpecialEffects;
import com.plumejade.lensouls.network.ElementSpiralPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;

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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
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

        // 减速标记现记录在镜魂 DoT 效果侧（照片活性不携带减速）
        boolean slowness = isPlayer && SoulDotEffect.hasPlayerSlowness(player);

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

            // 攻击者药水活性（玩家或实体均可挂灌注；镜魂 DoT 增益同样贡献活性）
            if (attacker instanceof LivingEntity livingAttacker) {
                for (MobEffectInstance inst : livingAttacker.getActiveEffects()) {
                    if (inst.getEffect().value() instanceof ElementInfusionEffect effect
                            && effect.getElement() == element) {
                        activitySum += ElementDamage.getActivityByAmplifier(inst.getAmplifier());
                        break;
                    }
                }
                for (MobEffectInstance inst : livingAttacker.getActiveEffects()) {
                    if (inst.getEffect().value() instanceof SoulDotEffect effect
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

            // 玩家侧副作用：水灌注减速（云筑魔像镜魂）
            if (isPlayer) {
                if (slowness && element == ElementDamage.WATER) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, true));
                }
            }

            if (activitySum <= 0f) continue;

            // 照片元素强化（佩戴者造成）：照片 mob 的 attacker_element 等级 ×3% 追伤
            if (isPlayer && player instanceof ServerPlayer serverPlayer) {
                totalBonusMultiplier += PhotoSpecialEffects.getPhotoElementBonus(serverPlayer, element);
            }

            // 免伤对抗：受击方药水等级 ≥ 攻击方等级 → 该元素追加完全免疫
            if (attackerLevel > 0 && getPotionLevel(target, element) >= attackerLevel) continue;

            float weakness = DataPackLoader.getWeakness(entityId, element);

            // 照片元素弱点（佩戴者受到）：读自定义弱点属性（由 PhotoSpecialEffects 每 tick 核算）
            // 属性基值 1.0、修饰为 ADD_MULTIPLIED_BASE → 实际系数 = 属性值 - 1（tooltip 显示为百分比）
            if (target instanceof ServerPlayer targetPlayer) {
                double photoWeak = switch (element) {
                    case FIRE -> targetPlayer.getAttributeValue(com.plumejade.lensouls.attribute.ModAttributes.FIRE_WEAKNESS);
                    case WATER -> targetPlayer.getAttributeValue(com.plumejade.lensouls.attribute.ModAttributes.WATER_WEAKNESS);
                    case EARTH -> targetPlayer.getAttributeValue(com.plumejade.lensouls.attribute.ModAttributes.EARTH_WEAKNESS);
                    case ENDER -> targetPlayer.getAttributeValue(com.plumejade.lensouls.attribute.ModAttributes.ENDER_WEAKNESS);
                    default -> 1.0;
                };
                weakness += (float) (photoWeak - 1.0);
            }

            if (weakness > 0f) {
                totalBonusMultiplier += activitySum * weakness;
                // 镜魂 DoT 元素不爆 up 螺旋（仅停粒子，追伤照常）；武器/照片活性等其他来源照常触发
                if (!(isPlayer && SoulDotEffect.hasActiveDot(player, element))) {
                    emitSpiralParticle(level, target, element);
                }
            }
        }

        // ── 弹射物弱点（PROJECTILE 元素，活性隐含 1.0）──
        // 触发口径 = 远程（活体攻击方的非直接命中或 IS_PROJECTILE），对齐「远程伤害加成词条」对远程伤害的定义。
        // 次元枪子弹不再算投射物（解除 AlphaYeti 类远程免疫），但其本身满足远程判定；isGunBullet 仅作无主/边界兜底。
        boolean isGunBullet = source.getDirectEntity() instanceof com.plumejade.lensouls.entity.GunBulletEntity;
        if (RangedAttackHelper.isRanged(source) || isGunBullet) {
            float projWeakness = DataPackLoader.getWeakness(entityId, ElementDamage.PROJECTILE);
            totalBonusMultiplier += projWeakness;
            if (projWeakness > 0f) emitSpiralParticle(level, target, ElementDamage.PROJECTILE);
        }

        // 恶意：折翼沉渊佩戴者受到的元素附加伤害 +12%（相对增幅）
        if (target instanceof ServerPlayer targetPlayer && FeatherAbyssHandler.hasAbyss(targetPlayer)) {
            totalBonusMultiplier *= 1.12f;
        }

        // 追加叠加在护甲后伤害上（护甲仍然有效）
        if (!level.isClientSide && totalBonusMultiplier > 0f) {
            float current = event.getNewDamage();
            event.setNewDamage(current + current * totalBonusMultiplier);
        }

        // 弱点武器匹配：目标有弱点但玩家武器元素（item_activity / 次元枪子弹元素）不匹配任一“非弹射物”弱点
        // → 最终伤害拦截为原始的 10%（空手 / 普通无元素武器同样拦截）
        // 弹射物弱点（PROJECTILE）活性隐含 1.0，无需武器匹配，不触发此惩罚
        if (!level.isClientSide && isPlayer) {
            Map<ElementDamage, Float> weaknesses = DataPackLoader.getAllWeaknesses(entityId);
            boolean needsMatch = false;
            boolean matches = false;
            for (ElementDamage weakElem : weaknesses.keySet()) {
                if (weakElem == ElementDamage.PROJECTILE) continue;   // 弹射物弱点无需武器匹配
                needsMatch = true;
                if (weaponId != null && ItemElementActivityLoader.getLevel(weaponId, weakElem) > 0) {
                    matches = true;
                    break;
                }
                if (bulletElement == weakElem) {
                    matches = true;
                    break;
                }
            }
            Entity sourceEntity = event.getSource().getEntity();
            boolean isGytrinket = sourceEntity != null && sourceEntity.getClass().getName().contains("gytrinket");
            // DoT 跳伤豁免：正在结算的 DoT 元素属于目标弱点集 → 不吃武器匹配 ×0.1
            ElementDamage dotElem = SoulDotHandler.getApplyingDotElement();
            // 克拉肯船炮豁免：block_factorys_bosses 的克拉肯船炮（kraken_cannon_item 发射的 cannonball）
            // 属于「场景武器」，不受上面这条武器元素匹配惩罚，伤害照常结算。
            boolean krakenCannon = isKrakenCannonball(event.getSource());
            if (needsMatch && !matches && !isGytrinket && !krakenCannon
                    && !(dotElem != null && weaknesses.containsKey(dotElem))) {
                float cap = event.getOriginalDamage() * 0.1f;
                if (event.getNewDamage() > cap) event.setNewDamage(cap);
            }
        }
    }

    /**
     * 是否为 block_factorys_bosses「克拉肯船炮」造成的伤害（用于豁免上面的 ×0.1 武器匹配惩罚）。
     * <p>
     * 该模组的炮弹实体是 {@code block_factorys_bosses:cannonball}，伤害类型是
     * {@code block_factorys_bosses:cannonball_hit}（见其 data 包），由 {@code kraken_cannon_item}
     * （大炮物品 / 船上的 CannonEntity）发射。三种特征任一命中即认定，全部按字符串比对，
     * 未安装该模组时自然恒为 false。
     */
    private static boolean isKrakenCannonball(DamageSource source) {
        if (source == null) return false;

        var typeKey = source.typeHolder().unwrapKey();
        if (typeKey.isPresent()) {
            ResourceLocation id = typeKey.get().location();
            if ("block_factorys_bosses".equals(id.getNamespace())
                    && ("cannonball_hit".equals(id.getPath()) || "cannonball".equals(id.getPath())
                        || "kraken_cannon_item".equals(id.getPath()))) {
                return true;
            }
        }

        for (Entity e : new Entity[]{source.getDirectEntity(), source.getEntity()}) {
            if (e == null) continue;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if ("block_factorys_bosses".equals(id.getNamespace())
                    && ("cannonball".equals(id.getPath()) || "kraken_cannon".equals(id.getPath()))) {
                return true;
            }
        }
        return false;
    }

    /** 获取实体身上指定元素的灌注/镜魂 DoT 药水等级（无 = 0） */
    private static int getPotionLevel(LivingEntity entity, ElementDamage element) {
        int level = 0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof ElementInfusionEffect effect
                    && effect.getElement() == element) {
                level = Math.max(level, inst.getAmplifier() + 1);
            } else if (inst.getEffect().value() instanceof SoulDotEffect effect
                    && effect.getElement() == element) {
                level = Math.max(level, inst.getAmplifier() + 1);
            }
        }
        return level;
    }

    /** 发射元素弱点螺旋粒子（仅显式配置的弱点） */
    private static void emitSpiralParticle(Level level, LivingEntity target, ElementDamage element) {
        if (!level.isClientSide && DataPackLoader.getAllWeaknesses(
                BuiltInRegistries.ENTITY_TYPE.getKey(target.getType())).containsKey(element)) {
            PacketDistributor.sendToPlayersTrackingEntity(target,
                    new ElementSpiralPacket(target.getId(), element.ordinal(), false));
        }
    }


}
