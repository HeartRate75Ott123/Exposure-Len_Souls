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
import com.plumejade.lensouls.util.WeaknessLensPhoto;
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

        // 弱点透镜照片：装上武器后视为「照片记录元素」的 2 级武器活性（武器自身更高则按自身的）。
        // 一次读出供整轮元素循环复用（伤害结算全服热路径，不逐元素重复解析武器 NBT）。
        WeaknessLensPhoto.Installed weaponLens = isPlayer
                ? WeaknessLensPhoto.inspectActive(player.getMainHandItem(), level.registryAccess())
                : WeaknessLensPhoto.Installed.NONE;
        ElementDamage lensElement = weaponLens.element();

        // 佩戴照片的元素追伤加成：**一次算完四个元素**，供下面整轮元素循环查表。
        // 原实现把 getPhotoElementBonus 放在元素循环里逐元素调用，每次都要重扫 Curios 装备照片、
        // 逐张 copyTag、逐张 ResourceLocation.parse（一次命中最多四遍）。现在走
        // PhotoSetEffects 的 tick 级装备缓存 + id 只解析一次，无加成时是空表、零额外分配。
        // ⚠ 这里只是「把表算出来」：**生效条件仍在循环内的 activitySum 闸里**（逐元素独立），
        // 见下面元素循环里的注释——不要因为表在循环外就把加成当成无条件项。
        Map<ElementDamage, Float> photoElementBonuses = isPlayer && player instanceof ServerPlayer sp
                ? com.plumejade.lensouls.integration.PhotoSetEffects.computeElementBonuses(sp)
                : java.util.Map.of();

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
                int baseLevel = ItemElementActivityLoader.getLevel(weaponId, element);
                int lensLevel = lensElement == element ? WeaknessLensPhoto.ACTIVITY_LEVEL : 0;
                attackerLevel = Math.max(baseLevel, lensLevel);
                activitySum += ElementDamage.getActivityByLevel(attackerLevel);
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

            // 照片元素强化（佩戴者造成）：照片 mob 的 attacker_element 等级 ×3% 追伤。
            // ⚠ 必须在 activitySum 闸**之内**（逐元素独立）：这句话的语义是「该元素有活性时，
            // 佩戴照片再按元素追加」，不是「照片自己凭空加伤害」。曾经把加法提到循环外，
            // 结果空手/无该元素活性也能吃照片加成（火照片=无条件 +3%/级），是行为回归。
            // 现在只是把「算表」提到循环外（表在 photoElementBonuses，tick 级缓存一次算完），
            // 这里的判据与逐元素特性完全保持原样。
            if (isPlayer) {
                totalBonusMultiplier += photoElementBonuses.getOrDefault(element, 0f);
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
        boolean rangedHit = RangedAttackHelper.isRanged(source) || isGunBullet;
        if (rangedHit) {
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
            float elementBonus = current * totalBonusMultiplier;
            event.setNewDamage(current + elementBonus);
            // ③ 羽·元素觉醒者 整款共用反转条件：活性等级 ≥10 且生命 ≥90% 时造成一次 10000 点元素附加伤害
            if (attacker instanceof ServerPlayer bonusPlayer) {
                com.plumejade.lensouls.handler.FeatherElementRiseHandler.onElementBonus(bonusPlayer, elementBonus);
            }
        }

        // 弱点武器匹配：目标有「非弹射物」弱点（倍率 > 0）但玩家武器元素（item_activity / 次元枪子弹元素）
        // 不匹配任一 → 最终伤害拦截为原始的 10%（空手 / 普通无元素武器同样拦截）
        // 弹射物弱点（PROJECTILE）活性隐含 1.0：本次攻击本身就是弹射物/远程命中即视为匹配（不吃武器匹配惩罚）；
        // 「只有弹射物弱点」的怪物对非弹射物攻击也从不触发此惩罚（needsMatch 不统计弹射物与 0 值占位条目）
        if (!level.isClientSide && isPlayer) {
            Map<ElementDamage, Float> weaknesses = DataPackLoader.getAllWeaknesses(entityId);
            boolean needsMatch = false;
            // 弹射物命中自带匹配：只要目标显式配了弹射物弱点（倍率 > 0），弹射物命中就视为命中弱点，
            // 不再因「武器没有其它弱点元素活性」把整发弹射物伤害拦成 10%（弹射物增伤不被惩罚吃掉）
            boolean matches = rangedHit
                    && weaknesses.getOrDefault(ElementDamage.PROJECTILE, 0f) > 0f;
            for (ElementDamage weakElem : weaknesses.keySet()) {
                if (weakElem == ElementDamage.PROJECTILE) continue;   // 弹射物弱点无需武器匹配（见上）
                if (weaknesses.get(weakElem) <= 0f) continue;        // 0 值占位弱点：无增伤也不要求武器匹配
                needsMatch = true;
                // 武器活性（含弱点透镜照片提供的 2 级活性）匹配任一弱点即不算「不匹配」
                int weaponLevel = weaponId == null ? 0 : ItemElementActivityLoader.getLevel(weaponId, weakElem);
                if (lensElement == weakElem) weaponLevel = Math.max(weaponLevel, WeaknessLensPhoto.ACTIVITY_LEVEL);
                if (weaponLevel > 0) {
                    matches = true;
                    break;
                }
                if (bulletElement == weakElem) {
                    matches = true;
                    break;
                }
            }
            // gytrinket 无人机/构造体豁免：该模组几种无人机（无人机子弹 / 蜂群电弧 / 光束 / 近战 /
            // 僚机横扫 / 斩杀）造成的伤害来自它自己的构造体机制，不属于「玩家武器元素不匹配」，
            // 不吃 ×0.1。注意不能只看攻击者类名：无人机子弹的 causing entity 常常就是**玩家**
            // （构造体归玩家所有），必须同时看伤害类型命名空间与直接实体。
            boolean isGytrinket = isGytrinketDamage(event.getSource());
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
     * 是否为 gytrinket 构造体（无人机 / 蜂群 / 僚机）造成的伤害（用于豁免上面的 ×0.1 武器匹配惩罚）。
     * <p>
     * 三种特征任一命中即认定 —— 单看某一项都会漏（见每种特征后面的备注）：
     * <ol>
     *   <li><b>伤害类型命名空间是 {@code gytrinket}</b>：覆盖 {@code drone_bullet}
     *       （无人机子弹 / 爆炸弹。它的 causing entity 常常是<b>玩家</b>——构造体归玩家所有，
     *       所以只看攻击者类名会漏）、{@code swarm_damage}（蜂群电弧）、
     *       {@code execute_damage}（僚机斩杀，归属玩家）、{@code siphon_damage} 等该模组自有伤害类型；</li>
     *   <li>直接实体或攻击实体的<b>类名</b>以 {@code com.gytrinket.} 开头：覆盖它用原版
     *       {@code mobAttack} / {@code indirectMagic} 造伤害的光束与近战（伤害类型是原版的）；</li>
     *   <li>直接实体或攻击实体的<b>实体类型命名空间</b>是 {@code gytrinket}：与类名互为兜底
     *       （对方若改包名/类名，实体类型 id 仍稳定）。</li>
     * </ol>
     * 全部按字符串比对，未安装该模组时自然恒为 false。
     */
    private static boolean isGytrinketDamage(DamageSource source) {
        if (source == null) return false;

        var typeKey = source.typeHolder().unwrapKey();
        if (typeKey.isPresent() && "gytrinket".equals(typeKey.get().location().getNamespace())) {
            return true;
        }

        for (Entity e : new Entity[]{source.getDirectEntity(), source.getEntity()}) {
            if (e == null) continue;
            if (e.getClass().getName().startsWith("com.gytrinket.")) return true;
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
            if (id != null && "gytrinket".equals(id.getNamespace())) return true;
        }
        return false;
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
