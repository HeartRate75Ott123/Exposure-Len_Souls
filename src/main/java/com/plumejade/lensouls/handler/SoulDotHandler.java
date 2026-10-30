package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.SoulDotEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 镜魂 DoT 增益结算器。
 * <p>
 * 玩家身上有任一镜魂 DoT 效果（{@link SoulDotEffect}）时，攻击命中向目标附加持续伤害实例：
 * <ul>
 *   <li>每实例共 4 跳，每 {@link #TICK_INTERVAL} tick 一跳（首跳在命中后 5 tick，整段持续 20 tick）；</li>
 *   <li>每跳伤害 = 命中瞬间攻击面板（{@link Attributes#ATTACK_DAMAGE}）的 {@link #DAMAGE_FRACTION}，
 *       BOSS 镜魂（{@link SoulDotEffect#getPlayerSoulId} 非空）×2；</li>
 *   <li>同一目标并发实例上限 = 镜魂等级（amplifier + 1，1~5），满时顶掉最老实例；</li>
 *   <li>伤害按元素分流：火→{@code lensouls:soul_dot_fire}、水→{@code soul_dot_water}、
 *       土→{@code soul_dot_earth}、末影→{@code soul_dot_ender}（本模组自定义伤害类型，
 *       不再借用原版 in_fire/freeze/magic/wither——原版类型会被目标的抗火/元素免疫整段拒掉），
 *       攻击者记为伤害来源（击杀归属玩家）。</li>
 * </ul>
 * 增益结束前已挂上的实例自然走完；DoT 结算期间置 {@link #applyingDot} 护栏防止自触发。
 */
public class SoulDotHandler {

    /** 每跳伤害占攻击面板比例（BOSS 镜魂 ×2） */
    public static final float DAMAGE_FRACTION = 0.10f;
    /** 跳间隔（tick） */
    public static final int TICK_INTERVAL = 5;
    /** 每实例跳数（总持续 = 4 × 5 = 20 tick） */
    public static final int TOTAL_JUMPS = 4;

    /**
     * DoT 伤害类型按元素分流 —— **一律走本模组自定义的伤害类型**
     * （{@code data/lensouls/damage_type/soul_dot_*.json}），不再借用原版 in_fire / freeze / magic / wither。
     * <p>
     * 为什么必须自建：原版类型会被目标的「元素免疫」整段拒掉，最典型的是
     * {@code Entity.isInvulnerableTo} 里的 {@code source.is(IS_FIRE) && fireImmune()}——
     * 灾变几乎全部生物（含焰魔 / 下界合金巨兽）与其它模组的大量 BOSS 都是 fireImmune，
     * 火焰 DoT 打上去连 {@code LivingDamageEvent} 都不会派发，实机表现就是「DoT 完全不触发」。
     * 自定义类型不带 {@code is_fire} / {@code is_freezing} 等标签，元素归属改由
     * {@code data/lensouls/damage_type_element/soul_dot.json} 配置（activity 2.5），
     * 于是元素增伤、弱点判定照旧，抗火免疫不再生效。
     */
    private static final Map<ElementDamage, ResourceKey<DamageType>> DOT_DAMAGE_TYPES = Map.of(
            ElementDamage.FIRE, dotType("soul_dot_fire"),
            ElementDamage.WATER, dotType("soul_dot_water"),
            ElementDamage.EARTH, dotType("soul_dot_earth"),
            ElementDamage.ENDER, dotType("soul_dot_ender")
    );

    /** 本模组自定义 DoT 伤害类型的 ResourceKey（注册项在数据包 JSON 里，Java 侧只需键） */
    private static ResourceKey<DamageType> dotType(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, path));
    }

    /** 单个 DoT 实例（damagePerTick 在命中时按攻击面板快照） */
    private record DotInstance(ElementDamage element, float damagePerTick,
                               long nextTickGameTime, int jumpsLeft, UUID attackerId) {}

    /** 目标实体 → 元素 → DoT 实例队列（插入序 = 施加序，最老在前；各元素独立封顶，互不挤占） */
    private static final Map<LivingEntity, Map<ElementDamage, List<DotInstance>>> DOTS = new HashMap<>();

    /** 防递归护栏：本系统结算伤害期间不再触发新增 */
    private static boolean applyingDot = false;

    /** 当前正在结算的 DoT 元素（供 DamageHandler 豁免武器匹配 ×0.1），null = 非结算期 */
    private static ElementDamage applyingDotElement = null;

    /** DamageHandler 判断当前伤害是否为 DoT 跳伤及其元素 */
    public static ElementDamage getApplyingDotElement() {
        return applyingDotElement;
    }

    // ========== 命中时附加 ==========

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (applyingDot) return;
        if (event.getEntity().level().isClientSide) return;

        Entity caster = event.getSource().getEntity();
        if (!(caster instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;

        long now = target.level().getGameTime();
        for (MobEffectInstance inst : player.getActiveEffects()) {
            if (!(inst.getEffect().value() instanceof SoulDotEffect effect)) continue;
            ElementDamage element = effect.getElement();
            String soulId = SoulDotEffect.getPlayerSoulId(player, element);
            boolean boss = soulId != null && !soulId.isEmpty();
            int level = inst.getAmplifier() + 1;   // 镜魂等级 1~5
            float attack = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float perTick = attack * DAMAGE_FRACTION * (boss ? 2f : 1f);
            addDot(target, new DotInstance(element, perTick,
                    now + TICK_INTERVAL, TOTAL_JUMPS, player.getUUID()), level);
        }
    }

    /** 添加实例到该元素的队列，队列长度不超过 cap（满时顶掉最老实例）；不同元素互不挤占 */
    private static void addDot(LivingEntity target, DotInstance dot, int cap) {
        Map<ElementDamage, List<DotInstance>> byElement =
                DOTS.computeIfAbsent(target, k -> new EnumMap<>(ElementDamage.class));
        List<DotInstance> list = byElement.computeIfAbsent(dot.element(), k -> new ArrayList<>());
        while (list.size() >= Math.max(1, cap)) {
            list.remove(0);
        }
        list.add(dot);
    }

    // ========== 每 tick 结算 ==========

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DOTS.isEmpty()) return;
        for (var it = DOTS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            LivingEntity target = entry.getKey();
            if (!target.isAlive() || target.isRemoved() || target.level().isClientSide) {
                it.remove();
                continue;
            }
            long now = target.level().getGameTime();
            Map<ElementDamage, List<DotInstance>> byElement = entry.getValue();
            for (var elIt = byElement.values().iterator(); elIt.hasNext(); ) {
                List<DotInstance> list = elIt.next();
                var listIt = list.listIterator();
                while (listIt.hasNext()) {
                    DotInstance dot = listIt.next();
                    if (now < dot.nextTickGameTime()) continue;
                    applyDotTick(target, dot);
                    if (dot.jumpsLeft() - 1 <= 0) {
                        listIt.remove();
                    } else {
                        listIt.set(new DotInstance(dot.element(), dot.damagePerTick(),
                                dot.nextTickGameTime() + TICK_INTERVAL, dot.jumpsLeft() - 1, dot.attackerId()));
                    }
                }
                if (list.isEmpty()) elIt.remove();
            }
            if (byElement.isEmpty()) {
                it.remove();
            }
        }
    }

    /** 结算一跳：清无敌帧后按元素伤害类型施伤（攻击者记为来源） */
    private static void applyDotTick(LivingEntity target, DotInstance dot) {
        Holder.Reference<DamageType> typeRef;
        try {
            typeRef = target.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DOT_DAMAGE_TYPES.get(dot.element()));
        } catch (Exception e) {
            return;
        }
        ServerPlayer attacker = findAttacker(target, dot.attackerId());
        DamageSource source = new DamageSource(typeRef, null, attacker);

        // 照片套装「元素 DoT 增伤」乘区（无套装效果时为 1.0）
        // 羽·元素觉醒者 e2「余灰」：DoT 伤害随目标剩余血量百分比变化（最高 +100%、最低 -50%；反转后下限 -20%）
        float amount = dot.damagePerTick();
        if (attacker != null) {
            amount *= com.plumejade.lensouls.integration.PhotoSetEffects.getDotMultiplier(attacker, dot.element());
            amount *= FeatherElementRiseHandler.dotMultiplier(attacker, target);
        }

        applyingDot = true;
        applyingDotElement = dot.element();
        try {
            target.invulnerableTime = 0;   // DoT 跳不被无敌帧吞掉
            target.hurt(source, amount);
        } finally {
            applyingDot = false;
            applyingDotElement = null;
        }
    }

    /** 解析攻击者（离线则返回 null，DoT 伤害不再归属玩家） */
    private static ServerPlayer findAttacker(LivingEntity target, UUID attackerId) {
        var server = target.level().getServer();
        return server != null ? server.getPlayerList().getPlayer(attackerId) : null;
    }
}
