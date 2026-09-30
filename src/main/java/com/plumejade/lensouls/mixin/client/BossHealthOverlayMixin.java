package com.plumejade.lensouls.mixin.client;

import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.UUID;

/**
 * 防御性拦截：服务端下发的 boss 条<b>只读更新</b>包，若客户端 {@code events} 表里已无该条目，
 * 原版会 {@code events.get(id).setXxx()} 抛 NPE 并<b>直接断线</b>。
 *
 * <h3>崩溃是怎么发生的（实测日志已复现，时间戳对得上）</h3>
 * <pre>
 * BossHealthOverlay$1.updateProgress(BossHealthOverlay.java:130)   ← events.get(id) 返回 null
 *   ← ClientboundBossEventPacket$UpdateProgressOperation.dispatch
 *   ← BossHealthOverlay.update → ClientPacketListener.handleBossUpdate
 * java.lang.NullPointerException: ... because the return value of "java.util.Map.get(Object)" is null
 * Description: Packet handling error
 * </pre>
 * {@code BossHealthOverlay.reset()} 的实现就是 {@code events.clear()}，而登出/卸载世界时它会被调用；
 * 此时<b>已经排在渲染线程队列里的</b> boss 更新包随后才执行（{@code PacketUtils.ensureRunningOnSameThread}
 * 是延后执行的）⇒ 表已空、包还在途。日志里崩溃在 02:53:51、登出在 02:53:52，正是这条路径。
 *
 * <h3>为什么挂在这个类、这个位置（前一版全错在这，务必看清）</h3>
 * <ol>
 *   <li>{@code updateProgress/updateName/updateStyle/updateProperties} <b>不在 {@code BossHealthOverlay} 上</b>，
 *       而在<b>匿名内部类 {@code BossHealthOverlay$1}</b>（{@code ClientboundBossEventPacket.Handler} 的实现）。
 *       旧版写成 {@code @Mixin(BossHealthOverlay.class)} + {@code method = "updateProgress"}，
 *       方法根本不存在；再叠加 {@code require = 0} ⇒ <b>五条防线全部静默失效、从未拦截过一次</b>。
 *       现在用 {@code targets = "…BossHealthOverlay$1"} 精确指向。</li>
 *   <li>旧版参数写的是 {@code (ClientboundBossEventPacket)}，实际签名是
 *       {@code updateProgress(UUID, float)} / {@code updateName(UUID, Component)} /
 *       {@code updateStyle(UUID, BossBarColor, BossBarOverlay)} / {@code updateProperties(UUID, boolean×3)}；
 *       而且这个版本<b>没有 {@code updateFlags}</b>。这里改为重定向四次方法体内那条共同的
 *       {@code Map.get}，不依赖任何包私有类型。</li>
 *   <li><b>不能</b>改在 {@code BossHealthOverlay.update} 上 {@code @Inject} + {@code cancel}：
 *       guitween 对该方法里的 {@code packet.dispatch(handler)} 做了 {@code @WrapOperation}
 *       （其 {@code redirectUpdate}），一并取消会把别人的 boss 条动画废掉。本处只重定向
 *       {@code BossHealthOverlay$1} 内部的 {@code Map.get}，与之完全正交。</li>
 *   <li>{@code add}/{@code remove} 走的是 {@code Map.put}/{@code Map.remove}，不受本重定向影响 ⇒
 *       新 boss 条照常出现、移除照常生效。<b>只读更新在目标缺失时被导向一个「惰性占位条」</b>：
 *       它不在 {@code events} 里，因此永远不会被渲染，{@code setProgress/setName/…} 落在它身上只是
 *       无害的字段赋值，玩家的界面完全不受影响。</li>
 * </ol>
 * <p>
 * 四条 {@code @Redirect} 刻意拆开、各自 {@code require = 1}：这样任何一条将来对不上都会
 * <b>启动即报错</b>，而不是像旧版那样悄悄退化成「没有防护」——后者正是这次事故的根因。
 */
@Mixin(targets = "net.minecraft.client.gui.components.BossHealthOverlay$1")
public abstract class BossHealthOverlayMixin {

    /** 惰性占位 boss 条：不在 {@code events} 表中，永不渲染，只用来接住对已消失条目的更新 */
    @Unique
    private static LerpingBossEvent lensouls$inertBar;

    @Unique
    private static LerpingBossEvent lensouls$inertBar() {
        LerpingBossEvent bar = lensouls$inertBar;
        if (bar == null) {
            bar = new LerpingBossEvent(new UUID(0L, 0L), Component.empty(), 0.0F,
                    BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
            lensouls$inertBar = bar;
        }
        return bar;
    }

    /** 取条目，缺失时给占位条（原版此处直接 {@code .setXxx()} 会 NPE） */
    @Unique
    private static Object lensouls$eventOrInert(Map<UUID, LerpingBossEvent> events, Object id) {
        LerpingBossEvent event = events.get(id);
        return event != null ? event : lensouls$inertBar();
    }

    @Redirect(method = "updateProgress", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object lensouls$progress(Map<UUID, LerpingBossEvent> events, Object id) {
        return lensouls$eventOrInert(events, id);
    }

    @Redirect(method = "updateName", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object lensouls$name(Map<UUID, LerpingBossEvent> events, Object id) {
        return lensouls$eventOrInert(events, id);
    }

    @Redirect(method = "updateStyle", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object lensouls$style(Map<UUID, LerpingBossEvent> events, Object id) {
        return lensouls$eventOrInert(events, id);
    }

    @Redirect(method = "updateProperties", require = 1,
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object lensouls$properties(Map<UUID, LerpingBossEvent> events, Object id) {
        return lensouls$eventOrInert(events, id);
    }
}
