package com.plumejade.lensouls.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * 照片弹幕开关（玩家级）。
 * <p>
 * 架构照搬转换器的「模式切换」：客户端上报、<b>服务端权威</b>、存玩家自己的数据、切换时
 * 在物品栏上方（动作栏）回一行带当前按键名的提示。
 * <p>
 * 与转换器模式的一处结构差异：转换器模式挂在<b>转换器物品组件</b>上（逐物品独立、随物品走），
 * 而弹幕是玩家级功能、没有任何载体物品，所以状态落在玩家自己的 {@code persistentData}
 * （NeoForge 会把它写进玩家存档 ⇒ 重进游戏记得上次的选择）。
 * <p>
 * <b>键不存在 = 开启</b>：老存档与未切过的玩家一律保持「弹幕照旧触发」，
 * 不因这次新增开关而悄悄改变任何人的手感。
 */
public final class BarrageToggle {

    /** 玩家 persistentData 键；缺失即视为开启 */
    public static final String ENABLED_TAG = "lensouls:barrage_enabled";

    private BarrageToggle() {
    }

    public static boolean isEnabled(Player player) {
        if (player == null) return true;
        CompoundTag tag = player.getPersistentData();
        return !tag.contains(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
    }

    public static void setEnabled(Player player, boolean enabled) {
        if (player == null) return;
        player.getPersistentData().putBoolean(ENABLED_TAG, enabled);
    }

    /** 翻转并返回翻转后的值 */
    public static boolean flip(Player player) {
        boolean next = !isEnabled(player);
        setEnabled(player, next);
        return next;
    }
}
