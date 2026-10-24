package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 攻击链路诊断开关（`/lensouls curse debug true|false`）。
 * <p>
 * 用来定位「玩家完全打不出伤害 / 锈契丢失不涨进度」这类问题：把
 * ① 命中率掷骰、② ⑦ e3 锈契掷骰（含是否已被别的系统取消）、③ ⑦ 造成伤害收口
 * 三处各打一行日志，一眼就能看出伤害在哪一步消失。
 * <p>
 * 默认关闭，零开销（一个 volatile boolean）。
 */
public final class AttackDiag {

    private static volatile boolean enabled = false;

    private AttackDiag() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void log(String fmt, Object... args) {
        if (!enabled) return;
        LenSouls.LOGGER.info("[Atk] " + fmt, args);
    }

    /**
     * 观测点 0：事件**刚进派发链**（HIGHEST，比所有可能取消它的处理器都早）。
     * <p>
     * 与「锈契入口」（LOWEST）那一行对比即可判定伤害死在哪一段：
     * <ul>
     *   <li>这里都没有 ⇒ 攻击根本没走到 {@code hurt}（挥击/攻击被前置拦掉）；</li>
     *   <li>这里正常、锈契入口显示「已被取消=true」⇒ 中途被别的处理器取消；
     *       此时再按优先级定位（本模组在 HIGHEST 取消的只有法师胸针魔法化）；</li>
     *   <li>两行都正常但「造成伤害收口」最终=0 ⇒ 死在 {@code LivingDamageEvent.Pre}。</li>
     * </ul>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingHighest(LivingIncomingDamageEvent event) {
        if (!enabled) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        log("事件到达(最高优先级) 攻击者={} 目标={} dmg={} 已取消={} 伤害类型={}",
                player.getGameProfile().getName(), event.getEntity().getType(),
                event.getAmount(), event.isCanceled(),
                event.getSource().typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?"));
    }
}
