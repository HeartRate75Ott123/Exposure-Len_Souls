package com.plumejade.lensouls.integration;

import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * 滑翔伞（Paraglider）**精力值**的软依赖桥。
 * <p>
 * 纯反射 + {@link ModList} 门控：本模组对 Paraglider 没有编译依赖（`run/mods` 里也不带它），
 * 模组缺席时 {@link #available()} 恒为 false、{@link #ratio(Player)} 返回 {@link Double#NaN}，
 * 调用方据此直接跳过整条效果（不会抛异常、也不会把玩家当成"精力 0"）。
 * <p>
 * 对接的 API（`tictim.paraglider.api.stamina`，21.1.5 实测）：
 * <ul>
 *   <li>{@code Stamina.get(Player)} → 实例（服务端调用拿到的是服务端实例）</li>
 *   <li>{@code stamina()} 当前值 / {@code maxStamina()} 上限（默认一管 1000，可用精力容器扩容）</li>
 *   <li>{@code setStamina(double)} 写入（默认方法，内部走同步）</li>
 * </ul>
 */
public final class ParagliderStamina {

    private static final String MODID = "paraglider";
    private static final String API_CLASS = "tictim.paraglider.api.stamina.Stamina";

    /** 三态：null = 还没解析过 */
    private static Boolean loaded;
    private static Method apiGet;
    private static Method apiStamina;
    private static Method apiMax;
    private static Method apiSet;

    private ParagliderStamina() {
    }

    /** 模组是否在场 + API 是否解析成功（结果缓存，只解析一次） */
    public static boolean available() {
        resolve();
        return loaded != null && loaded;
    }

    /** 精力比例 0..1；模组缺席 / 玩家没有精力实例时返回 {@link Double#NaN} */
    public static double ratio(Player player) {
        if (player == null || !available()) return Double.NaN;
        try {
            Object stamina = apiGet.invoke(null, player);
            if (stamina == null) return Double.NaN;
            double cur = (Double) apiStamina.invoke(stamina);
            double max = (Double) apiMax.invoke(stamina);
            if (!(max > 0.0)) return Double.NaN;
            return Math.max(0.0, Math.min(1.0, cur / max));
        } catch (Throwable ignored) {
            return Double.NaN;
        }
    }

    /**
     * 消耗精力（不足则清零）。{@code amount} 是精力单位，不是比例。
     *
     * @return 实际消耗掉的量（模组缺席时返回 0）
     */
    public static double consume(Player player, double amount) {
        if (player == null || amount <= 0.0 || !available()) return 0.0;
        try {
            Object stamina = apiGet.invoke(null, player);
            if (stamina == null) return 0.0;
            double cur = (Double) apiStamina.invoke(stamina);
            double after = Math.max(0.0, cur - amount);
            apiSet.invoke(stamina, after);
            return cur - after;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static void resolve() {
        if (loaded != null) return;
        synchronized (ParagliderStamina.class) {
            if (loaded != null) return;
            try {
                if (!ModList.get().isLoaded(MODID)) {
                    loaded = false;
                    return;
                }
                Class<?> api = Class.forName(API_CLASS);
                apiGet = api.getMethod("get", Player.class);
                apiStamina = api.getMethod("stamina");
                apiMax = api.getMethod("maxStamina");
                apiSet = api.getMethod("setStamina", double.class);
                loaded = true;
            } catch (Throwable t) {
                loaded = false;
            }
        }
    }
}
