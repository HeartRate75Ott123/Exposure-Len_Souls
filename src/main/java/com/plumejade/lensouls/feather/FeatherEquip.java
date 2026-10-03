package com.plumejade.lensouls.feather;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * 羽毛「是否生效」的唯一判定入口。
 * <p>
 * <b>两个等价通道</b>（用户口径：羽毛可以安在 Curios，也可以装在我们的界面）：
 * <ol>
 *   <li>{@link FeatherAttachments#get} 的 5 个界面槽；</li>
 *   <li>Curios 的任意槽位（{@code findFirstCurio} 遍历所有槽）。</li>
 * </ol>
 * 「装进界面」在语义上<b>等效于把该物品装在正确的 curios 槽位上</b>，因此四个羽毛 handler
 * 的佩戴谓词全部改走本方法——它们不再各自直连 Curios。
 * <p>
 * <b>同种物品只生效一次</b>：本方法只回答布尔值，槽里/Curios 里塞几份都不会叠加
 * （这也让「既戴 Curios 又装界面」的重复场景自然收敛为一次）。
 * <p>
 * 判定顺序：先查我们的槽（纯内存数组，最便宜），再查 Curios。
 */
public final class FeatherEquip {

    private FeatherEquip() {
    }

    /** 玩家是否装配了该羽毛（界面槽 ‖ Curios） */
    public static boolean has(Player player, Item item) {
        if (player == null || item == null) return false;
        if (FeatherAttachments.get(player).contains(item)) return true;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(item)).isPresent())
                .orElse(false);
    }
}
