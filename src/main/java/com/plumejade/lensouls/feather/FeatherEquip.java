package com.plumejade.lensouls.feather;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * 诅咒「是否生效」的唯一判定入口。
 * <p>
 * <b>只认我们自己的界面槽</b>（{@link FeatherAttachments#get} 的 7 个槽）。
 * 早期版本还把 Curios 的任意槽位视为等价通道，现已**脱离 Curios**：
 * 道具不再是 {@code ICurioItem}、也从 {@code curios:curio} tag 里移除了，
 * 登录时由 {@code CurseMigrationHandler} 把旧存档里戴在 Curios 上的诅咒迁回背包。
 * <p>
 * <b>同种只生效一次</b>：本方法只回答布尔值，槽里塞几份都不会叠加。
 */
public final class FeatherEquip {

    private FeatherEquip() {
    }

    /** 玩家是否装配了该诅咒（只查我们的 7 个界面槽） */
    public static boolean has(Player player, Item item) {
        if (player == null || item == null) return false;
        return FeatherAttachments.get(player).contains(item);
    }
}
