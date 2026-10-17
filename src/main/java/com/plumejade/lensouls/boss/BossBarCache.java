package com.plumejade.lensouls.boss;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * BOSS 血条缓存工具。
 * <p>
 * 幻灵表演时要隐藏借来实体的血条。只调 {@code removeAllPlayers()} 不够——暮色的<b>幻影骑士</b>
 * 会在 {@code setNumber(0)} 里把 64 格内的玩家重新加回血条，而 {@code updateMyNumber()} /
 * {@code switchToFormation(ATTACK_PLAYER_START)} 在幻灵期间每 tick 都会走一次，
 * 于是「移除 → 又被加回来 → 血条一直看得见」。
 * <p>
 * 因此这里把幻灵的血条<b>登记</b>进一张弱引用表，再由
 * {@code mixin/ServerBossEventMixin} 与 {@code mixin/compat/ServerTFBossBarMixin}
 * 在 {@code addPlayer} 的 HEAD 直接拒绝——从源头断掉重加（原版 {@code ServerBossEvent} 与
 * 暮色覆写的 {@code ServerTFBossBar} 两条路径都要掐，后者不调用父类实现）。
 * {@link #clearIfPlayers} 再作为第三方自研血条的兜底。
 */
public final class BossBarCache {

    /**
     * 幻灵借体 BOSS 的血条（弱引用：实体丢弃后条目自动消失，不会把实体/血条留在静态表里）。
     * 用 {@code WeakHashMap} 的天然身份语义（{@code ServerBossEvent} 没有覆写 equals/hashCode）。
     */
    private static final Set<ServerBossEvent> PHANTOM_BARS =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private BossBarCache() {}

    /** 该血条是否属于幻灵（登记后 addPlayer 会被 mixin 拒绝）。 */
    public static boolean isPhantomBar(ServerBossEvent bar) {
        return bar != null && PHANTOM_BARS.contains(bar);
    }

    /**
     * 登记 + 清空：幻灵加入世界后调用一次。
     * 之后任何 {@code addPlayer}（含每 tick 重加）都会被 addPlayer mixin 挡掉。
     */
    public static void clearBossBar(Entity entity) {
        forEachBossBar(entity, bar -> {
            PHANTOM_BARS.add(bar);
            bar.removeAllPlayers();
        });
    }

    /** 兜底清理：血条上又有玩家了才移除（正常应被 addPlayer mixin 挡掉；给第三方自研血条留的保险）。 */
    public static void clearIfPlayers(Entity entity) {
        forEachBossBar(entity, bar -> {
            if (!bar.getPlayers().isEmpty()) bar.removeAllPlayers();
        });
    }

    /** 幻灵结束：解除登记。 */
    public static void unmarkBossBar(Entity entity) {
        forEachBossBar(entity, PHANTOM_BARS::remove);
    }

    /** 反射遍历实体及其父类的 {@code ServerBossEvent} 字段（血条字段在各 BOSS 基类里，名字不统一）。 */
    private static void forEachBossBar(Entity entity, Consumer<ServerBossEvent> action) {
        if (entity == null) return;
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (!ServerBossEvent.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                try {
                    ServerBossEvent bar = (ServerBossEvent) field.get(entity);
                    if (bar != null) action.accept(bar);
                } catch (IllegalAccessException ignored) {
                    // 跳过无法访问的字段
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
