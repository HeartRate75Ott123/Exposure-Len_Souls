package com.plumejade.lensouls.util;

import net.minecraft.world.entity.Entity;

/**
 * 末影龙「赐福水晶」标记 —— 本模组召唤的装饰性 {@code EndCrystal} 必须完全免疫伤害。
 * <p>
 * <b>为什么必须免疫</b>（{@code javap -c} 在实机 jar 的
 * {@code net.minecraft.world.entity.boss.enderdragon.EndCrystal} 上核实）：{@code hurt()} 第一句是
 * {@code isInvulnerableTo(source)}，通过之后会
 * {@code level.explode(this, source, null, x, y, z, 6.0F, false, ExplosionInteraction.BLOCK)} ——
 * <b>6.0 力度、破坏方块</b>。玩家在自家水晶旁边挥砍（或被别的弹幕/敌人顺手打到）就会
 * 被自己召唤的水晶炸伤、并把地形炸掉。
 * <p>
 * {@code setInvulnerable(true)} 只能挡「非创造玩家、非 BYPASSES_INVULNERABILITY」的那部分
 * （{@code Entity.isInvulnerableTo} 里这两个条件是放行的），创造模式挥一刀照样炸。
 * 所以最终防线是 {@code mixin/BlessingCrystalMixin} 对本标记的实体把 {@code hurt} 直接置 false。
 * <p>
 * 键名走常量，避免「改了一处、另一处还在读旧字面量」。
 */
public final class BlessingCrystalMarker {

    /** 实体 persistentData 键 */
    public static final String KEY = "lensouls:blessing_crystal";

    private BlessingCrystalMarker() {}

    public static void mark(Entity entity) {
        entity.getPersistentData().putBoolean(KEY, true);
    }

    public static boolean isMarked(Entity entity) {
        return entity.getPersistentData().getBoolean(KEY);
    }
}
