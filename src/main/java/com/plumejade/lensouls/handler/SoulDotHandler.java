package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.SoulDotEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
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
 *   <li>伤害按元素分流：火→inFire、水→freeze、土→magic、末影→wither，攻击者记为伤害来源（击杀归属玩家）。</li>
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

    /** DoT 伤害类型按元素分流 */
    private static final Map<ElementDamage, ResourceKey<DamageType>> DOT_DAMAGE_TYPES = Map.of(
            ElementDamage.FIRE, DamageTypes.IN_FIRE,
            ElementDamage.WATER, DamageTypes.FREEZE,
            ElementDamage.EARTH, DamageTypes.MAGIC,
            ElementDamage.ENDER, DamageTypes.WITHER
    );

    /** 单个 DoT 实例（damagePerTick 在命中时按攻击面板快照） */
    private record DotInstance(ElementDamage element, float damagePerTick,
                               long nextTickGameTime, int jumpsLeft, UUID attackerId) {}

    /** 目标实体 → DoT 实例队列（插入序 = 施加序，最老在前） */
    private static final Map<LivingEntity, List<DotInstance>> DOTS = new HashMap<>();

    /** 防递归护栏：本系统结算伤害期间不再触发新增 */
    private static boolean applyingDot = false;

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

    /** 添加实例，队列长度不超过 cap（满时顶掉最老实例） */
    private static void addDot(LivingEntity target, DotInstance dot, int cap) {
        List<DotInstance> list = DOTS.computeIfAbsent(target, k -> new ArrayList<>());
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
            List<DotInstance> list = entry.getValue();
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
            if (list.isEmpty()) {
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

        applyingDot = true;
        try {
            target.invulnerableTime = 0;   // DoT 跳不被无敌帧吞掉
            target.hurt(source, dot.damagePerTick());
        } finally {
            applyingDot = false;
        }
    }

    /** 解析攻击者（离线则返回 null，DoT 伤害不再归属玩家） */
    private static ServerPlayer findAttacker(LivingEntity target, UUID attackerId) {
        var server = target.level().getServer();
        return server != null ? server.getPlayerList().getPlayer(attackerId) : null;
    }
}
