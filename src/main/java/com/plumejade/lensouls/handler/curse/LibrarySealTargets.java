package com.plumejade.lensouls.handler.curse;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Set;

/**
 * ⑦ e5「誓约·封库」要拦下的两个第三方界面 —— **软依赖**，只认注册表 key。
 * <p>
 * 这里<b>刻意不引用任何模组类</b>（不用 {@code BackpackContainer} / {@code DimensionsNetMenu} 之类）：
 * 缺模组时要能正常加载，也要能在 jar 改名 / 类挪包后继续工作。判定口径是
 * {@link AbstractContainerMenu#getType()} 在 {@code BuiltInRegistries.MENU} 里的注册名。
 * <p>
 * <b>key 的出处（不是猜的，逐个用 {@code javap -p -c} 从实测 jar 的静态块里读出来的）：</b>
 * <ul>
 *     <li>精妙背包 {@code sophisticatedbackpacks-1.21.1-3.25.73.2020.jar}：
 *         {@code ModItems.MENU_TYPES}（{@code DeferredRegister.create(BuiltInRegistries.MENU, "sophisticatedbackpacks")}）
 *         在静态块里 {@code register("backpack", …)} → 字段 {@code BACKPACK_CONTAINER_TYPE}
 *         → {@code BackpackContainer} → 客户端界面 {@code client/gui/BackpackScreen}；
 *         同块 {@code register("settings", …)} → {@code SETTINGS_CONTAINER_TYPE}
 *         → {@code BackpackSettingsContainerMenu} → {@code BackpackSettingsScreen}。</li>
 *     <li>超越维度 {@code beyonddimensions-1.21.1-neoforge-0.7.24.jar}：各 Menu 自己的静态块
 *         （{@code DeferredRegister.create(Registries.MENU, "beyonddimensions")}）：
 *         {@code DimensionsNetMenu → "dimensions_net_menu"}、
 *         {@code DimensionsCraftMenu → "dimensions_craft_menu"}、
 *         {@code DimensionsCraftMenuTerminal → "dimensions_craft_menu_terminal"}。
 *         三者依次对应客户端 {@code DimensionsNetGUI / DimensionsCraftGUI / DimensionsTerminalCraftGUI}，
 *         也就是「维度网络」的存储界面（快捷键 {@code OpenNetGuiPacket} 走的就是前两个）。
 *         机器方块界面（{@code net_magnet_menu / net_feeder_menu / net_control_menu …}）不在封库范围，
 *         它们是过滤/配置界面，不是「打开维度网络存取物品」。</li>
 * </ul>
 * <p>
 * 服务端（{@code PlayerContainerEvent.Open}）与客户端（{@code ScreenEvent}）共用这一份名单，
 * 避免两边各写一份、以后只改了一处。
 */
public final class LibrarySealTargets {

    /** 被 e5 封印的菜单注册名（{@code BuiltInRegistries.MENU} 的 key，含命名空间） */
    public static final Set<String> MENU_KEYS = Set.of(
            // ── 精妙背包 ──
            "sophisticatedbackpacks:backpack",
            "sophisticatedbackpacks:settings",
            // ── 超越维度（维度网络）──
            "beyonddimensions:dimensions_net_menu",
            "beyonddimensions:dimensions_craft_menu",
            "beyonddimensions:dimensions_craft_menu_terminal"
    );

    /** 异常只报一次（每 tick / 每次开屏都会走这里，刷屏会把日志冲掉） */
    private static volatile boolean loggedOnce;

    private LibrarySealTargets() {
    }

    /**
     * 该菜单是否属于「精妙背包 / 维度网络」界面。
     * <p>
     * 任何异常（未注册的 {@code MenuType}、{@code getKey} 返回 null、注册表未就绪）一律返回 false：
     * 封库绝不能因为第三方模组状态异常而把无关界面拦掉或抛进容器打开链。
     */
    public static boolean isSealedMenu(AbstractContainerMenu menu) {
        if (menu == null) return false;
        try {
            MenuType<?> type = menu.getType();
            if (type == null) return false;
            ResourceLocation id = BuiltInRegistries.MENU.getKey(type);
            return id != null && MENU_KEYS.contains(id.toString());
        } catch (Throwable t) {
            logOnce("[Curse] ⑦ e5 封库菜单判定异常", t);
            return false;
        }
    }

    /** 异常（含 LinkageError）只打印一次 */
    public static void logOnce(String message, Throwable t) {
        if (loggedOnce) return;
        loggedOnce = true;
        LenSouls.LOGGER.error(message + "（同类错误后续不再打印）", t);
    }
}
