package com.plumejade.lensouls.handler.curse;

import net.minecraft.server.level.ServerPlayer;

/**
 * 「这一次不死图腾不许生效」的统一判定。
 * <p>
 * 目前**只有一个来源：⑤ e21 无援（诅咒态）** —— 它的文案就是「不死图腾失效」，任何死亡都不许图腾救。
 * <p>
 * <b>刻意不包含 ② e3 狂喜</b>（玩家口径）：狂喜的濒死是**有代价的**（图腾本身就会被消耗），
 * 让它先触发才符合玩家预期。而且图腾是在 {@code LivingEntity.hurt} 里、{@code die()} 之前生效，
 * 救活之后这一刀就不再是「死亡」，狂喜的强制掉落/阻死因此不会发生 —— 这正是期望行为。
 * 唯一例外是反转态「阻死到点」那 999 点真伤：它走 {@code genericKill}（{@code BYPASSES_INVULNERABILITY}），
 * 本来就吃不到图腾，与这里无关。
 * 由两个 mixin 使用：{@code TotemAnnulledMixin}（原版 {@code checkTotemDeathProtection} 入口）
 * 与 {@code compat/InventoryTotemCurseMixin}（《更好的图腾》背包消耗路径）。
 */
public final class TotemGuard {

    private TotemGuard() {
    }

    /** 拦截原因；null = 允许图腾生效 */
    public static String reason(ServerPlayer player) {
        if (player == null) return null;
        if (TimecoreCurse.blocksTotem(player)) return "⑤e21无援";
        return null;
    }

    public static boolean blocks(ServerPlayer player) {
        return reason(player) != null;
    }
}
