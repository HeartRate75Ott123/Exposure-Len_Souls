package com.plumejade.lensouls.damage;

import com.plumejade.lensouls.attribute.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 命中率（{@link ModAttributes#HIT_CHANCE}）结算。
 * <p>
 * 照片饰品以百分比加减命中率（弹幕类 -24%、小白/唤魔者 -6%、其余 +7%~+14%），
 * 这里在<b>玩家造成伤害时按最终命中率掷骰</b>：未命中则整次伤害彻底不成立，
 * 命中则原样走正常伤害流程（不做任何增减）。默认 100% 且上限 100%。
 * <p>
 * <b>钩子选择</b>：用 {@link LivingIncomingDamageEvent} 而不是 {@code LivingDamageEvent.Pre}。
 * 后者既不可取消，且触发时受伤方已经进入受击流程——把伤害置 0 仍会白送 20 tick 无敌帧
 * 与受击动画（等于"打空了却帮对手挡住下一刀"）。{@code LivingIncomingDamageEvent}
 * 位于受伤流程最前端且可取消，取消后等同于这次攻击从未发生。
 * <p>
 * <b>玩家归属判定</b>：覆盖全部源自玩家的伤害源，不留漏网——
 * 近战（造成者即玩家）、玩家发射的投射物/投掷物、玩家召唤或拥有的实体
 * （{@link OwnableEntity#getOwner()}）。非玩家来源（怪物、环境、其他玩家）一律不掷骰。
 * <p>
 * 命中率为 100% 时直接返回，不掷骰、零开销（绝大多数玩家不戴惩罚照片）。
 */
public class HitChanceHandler {

    /**
     * 最终命中率的**下限**。
     * <p>
     * 照片的命中率词条是「每张一条 {@code ADD_MULTIPLIED_BASE}」——弹幕类 −24%、幻象类 −12%、
     * 弱弹幕 −6%，多条叠加后属性会被压到 0（{@code RangedAttribute} 的 min 就是 0）。
     * 一旦压到 0，本处理器会取消**每一次**攻击：玩家「完全打不出伤害」，而这条惩罚的原意
     * 只是「命中率下降」。更隐蔽的是，它发生在 {@code LOWEST} 且注册早于诅咒处理器，
     * 被取消的事件不会再派发给后面的监听器 —— ⑦ e3 锈契因此**看不到**这些丢失，
     * 表现为「15% 丢失根本不触发、进度不涨」。
     * <p>
     * 所以这里给一个下限：照片叠再多，也至少保留 25% 命中率（想调整只改这一个常量）。
     */
    public static final double MIN_EFFECTIVE_CHANCE = 0.25;

    /** 命中率被压到下限时的 WARN 节流（UUID → 下次允许打印的游戏刻） */
    private static final java.util.Map<java.util.UUID, Long> WARN_AT = new java.util.concurrent.ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ServerPlayer attacker = resolvePlayerAttacker(event.getSource().getEntity(), event.getSource().getDirectEntity());
        if (attacker == null) return;
        // 自伤不算"攻击"，不参与命中判定
        if (event.getEntity() == attacker) return;

        AttributeInstance instance = attacker.getAttribute(ModAttributes.HIT_CHANCE);
        if (instance == null) return;

        double raw = instance.getValue();
        if (raw >= 1.0) return;

        double chance = Math.max(MIN_EFFECTIVE_CHANCE, Math.min(1.0, raw));
        if (raw < MIN_EFFECTIVE_CHANCE) {
            // 只在被压穿下限时按 30 秒节流记一次，方便定位「谁把命中率堆成负的」
            long now = attacker.level().getGameTime();
            Long until = WARN_AT.get(attacker.getUUID());
            if (until == null || now >= until) {
                WARN_AT.put(attacker.getUUID(), now + 600L);
                com.plumejade.lensouls.LenSouls.LOGGER.warn(
                        "[HitChance] 命中率属性={} 已低于下限 {}，按 {} 结算（玩家={}；多为弹幕/幻象类照片叠满所致）",
                        String.format("%.3f", raw), MIN_EFFECTIVE_CHANCE, MIN_EFFECTIVE_CHANCE,
                        attacker.getGameProfile().getName());
            }
        }

        boolean miss = attacker.getRandom().nextDouble() >= chance;
        if (miss) {
            event.setCanceled(true);
        }
        com.plumejade.lensouls.handler.curse.AttackDiag.log(
                "命中率 raw={} 取用={} 掷骰={} → {} 目标={}",
                String.format("%.3f", raw), String.format("%.3f", chance),
                miss ? "未命中" : "命中", miss ? "取消伤害" : "放行",
                event.getEntity().getType());
    }

    /**
     * 解析这次伤害是否「源自某个玩家」。
     *
     * @param causing 伤害的造成者（投射物会解析为其发射者）
     * @param direct  伤害的直接实体（箭、雪球、弹幕等）
     * @return 负责的玩家；不是玩家来源则 null
     */
    private static ServerPlayer resolvePlayerAttacker(Entity causing, Entity direct) {
        if (causing instanceof ServerPlayer player) return player;
        if (direct instanceof ServerPlayer player) return player;
        // 玩家拥有的实体：投掷物/投射物/区域效果云/被驯服生物等
        if (direct instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        if (causing instanceof OwnableEntity ownable && ownable.getOwner() instanceof ServerPlayer player) {
            return player;
        }
        return null;
    }
}
