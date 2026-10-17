package com.plumejade.lensouls.client.curse;

import com.plumejade.lensouls.client.CurseClientCache;
import com.plumejade.lensouls.feather.CurseDefs;
import com.plumejade.lensouls.handler.curse.LibrarySealTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Set;

/**
 * ⑦ e5「誓约·封库」的<b>客户端</b>拦截：检测到「精妙背包 / 维度网络」界面就自动关掉它。
 * <p>
 * 为什么不能只在右键入口拦：快捷键（精妙背包的开包键位、超越维度的 {@code OPEN_GUI_KEY}）根本
 * 不经过 {@code PlayerInteractEvent}，服务端也没法逐帧知道「玩家正要开哪个界面」。所以：
 * <ul>
 *     <li>{@link ScreenEvent.Opening}：界面还没建起来就掐掉（最常见路径，连闪一下都不会有）；</li>
 *     <li>{@link ScreenEvent.Init.Post}：界面已经 init 完（即已经开出来了）时登记关闭；</li>
 *     <li>{@link ClientTickEvent.Post}：每 tick 复核「当前屏幕是不是目标界面 ∧ 是否封库态」——
 *         这是唯一能覆盖「界面<b>先开着</b>、封库态<b>后到</b>（刚戴上羽毛 / 登录后镜像才同步过来）」
 *         的时机：那种情况下 {@code Opening} 与 {@code Init.Post} 都不会再触发。</li>
 * </ul>
 * <p>
 * <b>封库态判定</b>：{@link CurseClientCache#on}（= ⑦ 被装进槽位 ∧ e5 未反转），
 * 与 {@code CurseManager#on} 同口径；<b>不读服务端附件</b>（羽毛槽附件刻意不同步到客户端）。
 * 因此反转（解锁）后三处一律放行，不做任何额外判断。
 * <p>
 * <b>软依赖</b>：界面判定先走菜单注册名（{@link LibrarySealTargets#isSealedMenu}），
 * 退路走类名（{@link #SEALED_SCREEN_CLASSES}）。全程不 import 第三方类，缺模组时整条链路空转。
 */
public final class LibrarySealScreenHandler {

    /**
     * 「精妙背包 / 维度网络」界面的类全名（软依赖，字符串匹配，含父类遍历）。
     * <p>
     * 实际来源（{@code javap} 从实测 jar 读出的 {@code extends} 关系）：
     * <ul>
     *     <li>{@code sophisticatedbackpacks-3.25.73}：
     *         {@code BackpackScreen extends StorageScreenBase<BackpackContainer>}、
     *         {@code BackpackSettingsScreen extends SettingsScreen}（两者都是
     *         {@code AbstractContainerScreen}）；</li>
     *     <li>{@code beyonddimensions-0.7.24}：{@code BDBaseGUI extends AbstractContainerScreen}，
     *         {@code DimensionsNetGUI extends BDBaseGUI}，{@code DimensionsCraftGUI extends DimensionsNetGUI}，
     *         {@code DimensionsTerminalCraftGUI extends DimensionsCraftGUI}。
     *         ⇒ 认 {@code DimensionsNetGUI} 一个父类就同时覆盖「网络 / 合成 / 终端」三种存储界面，
     *         以后对方再加子类也自动命中。</li>
     * </ul>
     * 这里只列「打开维度网络存取物品」的那几个；磁铁/馈送器/泵之类的机器方块界面刻意不算。
     */
    private static final Set<String> SEALED_SCREEN_CLASSES = Set.of(
            "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackScreen",
            "net.p3pp3rf1y.sophisticatedbackpacks.client.gui.BackpackSettingsScreen",
            "com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI",
            "com.wintercogs.beyonddimensions.client.gui.DimensionsCraftGUI",
            "com.wintercogs.beyonddimensions.client.gui.DimensionsTerminalCraftGUI"
    );

    /** 已被 {@code Init.Post} 确认过的屏幕（下一 tick 免去再查注册表/类名） */
    private static Screen confirmedScreen;

    private LibrarySealScreenHandler() {
    }

    // ==================== 事件 ====================

    /** 界面即将打开：直接取消（{@code setNewScreen(null)} = 不建这个屏幕） */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        try {
            if (!sealed()) return;
            Screen newScreen = event.getNewScreen();
            if (newScreen == null || !isTargetScreen(newScreen)) return;
            // 1.21.1 的取消方式：把「要开的屏幕」置空（等价于什么都不开）。
            // 这里不做 setScreen / closeContainer —— 正处在 Minecraft#setScreen 的调用栈里，
            // 重入会打乱 screen 字段的赋值；容器菜单由服务端 Open 兜底关（见 ZeroforgeCurse）。
            event.setNewScreen(null);
            confirmScreen(null);
            notice();
        } catch (Throwable t) {
            LibrarySealTargets.logOnce("[Curse] ⑦ e5 封库（客户端 Opening）异常", t);
        }
    }

    /** 界面已 init 完（= 已经开出来了）：登记到下一 tick 关闭 */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        try {
            if (!sealed()) return;
            Screen screen = event.getScreen();
            if (screen == null || !isTargetScreen(screen)) return;
            // 不在 Screen#init 内部调 setScreen/onClose：Screen#init 是 Minecraft#setScreen 的调用栈中间，
            // 在这里关屏会重入 setScreen。登记下来，onClientTick 统一关。
            confirmScreen(screen);
        } catch (Throwable t) {
            LibrarySealTargets.logOnce("[Curse] ⑦ e5 封库（客户端 Init.Post）异常", t);
        }
    }

    /**
     * 每 tick 复核：封库态下只要「目标界面」还开着就关掉。
     * <p>
     * 这条才是真正的兜底 —— 覆盖「界面先开着、封库态后到」的情形（{@code Opening}/{@code Init.Post}
     * 那时都已经过去，不会再触发）。绝大多数 tick 只做一次 {@code screen == null} 判断就返回。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Screen screen = mc.screen;
            if (screen == null) {
                confirmScreen(null);
                return;
            }
            if (!sealed()) {
                confirmScreen(null);
                return;
            }
            // Init.Post 已经确认过的同屏：跳过注册表/类名复查；否则现查（覆盖「先开着再造出封库」）
            boolean confirmed = confirmedScreen == screen;
            if (!confirmed && !isTargetScreen(screen)) {
                confirmScreen(null);
                return;
            }
            closeSealed(mc, screen);
        } catch (Throwable t) {
            LibrarySealTargets.logOnce("[Curse] ⑦ e5 封库（客户端 tick）异常", t);
        }
    }

    // ==================== 内部 ====================

    /**
     * 关闭被封库拦下的界面。
     * <p>
     * 走原版关屏路径 {@link Screen#onClose()}：{@code AbstractContainerScreen#onClose}
     * → {@code LocalPlayer#closeContainer}（发 {@code ServerboundContainerClosePacket} 并复位
     * {@code containerMenu}），随后 {@code Screen#onClose} 内部 {@code setScreen(null)}。
     * 服务端收到关闭包会把容器菜单一起收尾，客户端与服务端不会各留一半状态。
     */
    private static void closeSealed(Minecraft mc, Screen screen) {
        confirmScreen(null);
        screen.onClose();
        if (mc.screen == screen) {
            mc.setScreen(null);      // onClose 内部已置空过，这里只是兜底（幂等）
        }
        notice();
    }

    /** 封库态：⑦ 装进槽位 ∧ e5 未反转（{@code CurseManager#on} 的客户端等价判断） */
    private static boolean sealed() {
        return CurseClientCache.on(CurseDefs.ZEROFORGE, 4);
    }

    /** 该屏幕是否属于「精妙背包 / 维度网络」 */
    private static boolean isTargetScreen(Screen screen) {
        if (screen == null) return false;
        // ① 先认菜单注册名（最稳，与「哪个界面类」解耦）
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            if (LibrarySealTargets.isSealedMenu(containerScreen.getMenu())) return true;
        }
        // ② 退路：类名（含父类遍历，覆盖对方新增的子类）
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            if (SEALED_SCREEN_CLASSES.contains(type.getName())) return true;
        }
        return false;
    }

    private static void confirmScreen(Screen screen) {
        confirmedScreen = screen;
    }

    /** 动作栏提示（文案沿用 e5 诅咒态已有的一条，不新增 lang 键） */
    private static void notice() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(
                Component.translatable("message.lensouls.zeroforge.library_sealed"), true);
    }
}
