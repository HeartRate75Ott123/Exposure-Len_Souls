package com.plumejade.lensouls.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.lensouls.client.feather.FeatherToast;
import com.plumejade.lensouls.client.feather.RoundedRect;
import io.github.mortuusars.exposure.Config;
import io.github.mortuusars.exposure.client.camera.CameraClient;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 相机取景器之上的两块<b>纯客户端叠加层</b>（在 HUD 之后、整帧最后画，所以必然压在最上层）。
 *
 * <h3>为什么不能挂在 {@code RenderGuiEvent} 上</h3>
 * Exposure 在 {@code Gui.render} 的 HEAD 画取景器，并且默认 {@code hide_hud_while_in_viewfinder = true}
 * 时<b>直接 cancel 掉整个 {@code Gui.render}</b>；而 {@code RenderGuiEvent} 是
 * {@code GuiLayerManager} 在 {@code Gui.render} <b>内部</b>发的 —— 取景器一开，这两个事件根本不会派发。
 * 所以叠加层改为注入 {@code GameRenderer.render} 里 {@code GuiGraphics.flush()} 之后（vanilla HUD / 屏幕 /
 * toast 全部画完、且 GUI 正交矩阵还没弹出），时机天然"最上层"。
 *
 * <h3>画什么</h3>
 * <ol>
 *   <li><b>冷却禁止符号</b>：<b>只在取景器界面里</b>（{@code CameraClient.viewfinder().isLookingThrough()}）
 *       且手持相机处于原版物品冷却时显示 —— 一个小号的「圆环 + 斜杠」禁止符
 *       （深玻璃圆盘衬底 + 细描边，配色沿用羽毛装配界面的调色板），位置在取景框底部控制条上方那条空带
 *       （屏幕高度 0.815），不挡准星也不挡主体。冷却一结束立刻淡出。</li>
 *   <li><b>取景器提示</b>：取景器开着时 {@code Gui} 被整个 cancel，动作栏消息（如
 *       {@code message.lensouls.hardman.inject_fail} "奇怪，这东西怎么用来着"）根本不会画出来 ——
 *       这里把这些消息接过来，用羽毛界面同款 {@link FeatherToast}（淡入 0.25s / 停留 2s / 淡出 0.5s）画在最上层。</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
public final class ViewfinderOverlay {

    /** 禁止符号：深玻璃圆盘（上/下两色，与羽毛面板同族） */
    private static final int DISC_TOP = 0x141A22;
    private static final int DISC_BOTTOM = 0x0E1218;
    /** 禁止符号：细描边 + 斜杠（羽毛界面里表示"不可用/已锁定"的警示红） */
    private static final int STROKE = 0xFF5A50;

    /**
     * 禁止符号的竖直位置（占屏幕高度比例）：对准取景框底部控制条（60° / 20mm / 16-16）上方那条空带，
     * 落在取景框画面里、又不会挡住准星与主体。
     */
    private static final float CENTER_Y_RATIO = 0.815F;
    /** 圆环半径（GUI 像素）：小图标，约 18px 直径 */
    private static final int RADIUS = 9;

    private static final float FADE_OUT_SECONDS = 0.22F;
    /**
     * 最短展示时长：Exposure 的快门冷却默认只有 2 tick（{@code CameraItem.BASE_COOLDOWN}），
     * 不兜底就只闪一帧、等于没画 —— 检出冷却后至少完整显示这么久再淡出。
     */
    private static final float HOLD_SECONDS = 0.35F;

    /** 禁止符号当前不透明度（冷却开始时瞬时拉满，结束后匀速淡出） */
    private static float alpha;
    /** 剩余"保底展示"时间（秒） */
    private static float hold;
    private static long lastNanos;

    private ViewfinderOverlay() {
    }

    // =====================================================================
    //  提示文字（动作栏消息）
    // =====================================================================

    /**
     * 接管一条动作栏消息：只在「原版根本画不出来」的时候才接管 ——
     * 有屏幕打开（HUD 不渲染），或者正在看取景器且 Exposure 会隐藏 HUD。
     * 其它情况仍交给原版动作栏，避免同一句话出现两份。
     * <p>
     * 例外：{@link #ALWAYS_TOAST_KEYS} 里这几条<b>永远</b>走最上层弹窗 ——
     * "无法造成韧性伤害（等级不足）"是拍照那一刻的关键反馈，但它由 {@code FrameAddedEvent} 触发，
     * 服务端回包时取景器往往已经关了（或客户端根本没开），于是它会掉回原版动作栏那条线上，
     * 被取景框/闪白压住看不见。这类消息固定接管，并把原版那一份清空避免重复。
     */
    public static void captureActionBar(Component text) {
        if (text == null || text.getString().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        boolean always = isAlwaysToast(text);
        if (always || mc.screen != null || hudHiddenByViewfinder()) {
            FeatherToast.show(text);
            // 已经用最上层弹窗画过了 → 清掉原版动作栏那份；取景器藏 HUD 时本来就没有，不用管
            if (always && !hudHiddenByViewfinder()) {
                mc.gui.setOverlayMessage(Component.empty(), false);
            }
        }
    }

    /** 必须走最上层弹窗的动作栏消息（按翻译键判定，不比对已翻译文本） */
    private static final java.util.Set<String> ALWAYS_TOAST_KEYS = java.util.Set.of(
            "message.lensouls.lens_tier_insufficient"
    );

    private static boolean isAlwaysToast(Component text) {
        return text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && ALWAYS_TOAST_KEYS.contains(t.getKey());
    }

    /** Exposure：正在通过取景器看 + 配置里隐藏 HUD ⇒ 原版动作栏/事件全都不会出现 */
    private static boolean hudHiddenByViewfinder() {
        try {
            var viewfinder = CameraClient.viewfinder();
            if (viewfinder == null || !viewfinder.isLookingThrough()) return false;
            return Config.Client.HIDE_HUD_WHILE_IN_VIEWFINDER.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =====================================================================
    //  渲染（由 GameRendererFrameEndMixin 在 GuiGraphics.flush() 之后调用）
    // =====================================================================

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.noRender) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
        lastNanos = now;

        // 只在"正在看取景器"时显示；冷却中瞬时显示、结束后先保底停留再淡出
        // （原版冷却常常只有 2 tick，靠淡出尾巴才能看清）
        boolean cooling = viewfinderActive() && cameraOnCooldown(mc.player);
        if (cooling) {
            alpha = 1.0F;
            hold = HOLD_SECONDS;
        } else if (hold > 0.0F) {
            hold = Math.max(0.0F, hold - dt);
        } else if (dt > 0.0F) {
            alpha = Math.max(0.0F, alpha - dt / FADE_OUT_SECONDS);
        }

        boolean toast = FeatherToast.isVisible();
        if (alpha <= 0.01F && !toast) return;

        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            if (alpha > 0.01F) {
                drawProhibition(graphics, width / 2, Math.round(height * CENTER_Y_RATIO), alpha);
            }
            // 提示文字最后画 —— 保证它压在最上面
            if (toast) {
                FeatherToast.render(graphics, width, height);
            }
        } finally {
            graphics.flush();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** 是否正在通过取景器看（Exposure 的取景界面不是 Screen，只能问它自己） */
    private static boolean viewfinderActive() {
        try {
            var viewfinder = CameraClient.viewfinder();
            return viewfinder != null && viewfinder.isLookingThrough();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 手持相机是否处于原版物品冷却（剑槽式 {@code ItemCooldowns}）。
     * 主手优先、副手兜底；两个相机同时在手上只要有一个在冷却就算。
     */
    private static boolean cameraOnCooldown(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isCoolingCamera(player, main)) return true;
        return isCoolingCamera(player, player.getOffhandItem());
    }

    private static boolean isCoolingCamera(LocalPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CameraItem)) return false;
        return player.getCooldowns().isOnCooldown(stack.getItem());
    }

    /**
     * 禁拍符号：深玻璃圆盘 + 细圆环 + 45° 斜杠。
     * 圆盘与圆环走 {@link RoundedRect}（半径 = 半边长 ⇒ 正圆），斜杠用旋转姿态下的 {@code fill} 画。
     */
    private static void drawProhibition(GuiGraphics graphics, int cx, int cy, float a) {
        int radius = RADIUS;
        int discHalf = radius + 3;

        // 底衬：圆盘（不透明度按 3/4 压暗，别盖住取景器本身）
        int discAlpha = clampByte(a * 0.72F);
        RoundedRect.roundedRect(graphics,
                cx - discHalf, cy - discHalf, discHalf * 2, discHalf * 2, discHalf,
                (discAlpha << 24) | DISC_TOP, (discAlpha << 24) | DISC_BOTTOM, 0, 0.0F);

        // 圆环
        RoundedRect.roundedOutline(graphics,
                cx - radius, cy - radius, radius * 2, radius * 2, radius,
                (clampByte(a) << 24) | STROKE, 1.6F);

        // 斜杠（-45°，从左下到右上不通 → 标准禁止符）
        int len = Math.round(radius * 1.62F);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0.0F);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-45.0F));
        graphics.fill(-len / 2, -1, len / 2, 1, (clampByte(a) << 24) | STROKE);
        graphics.pose().popPose();
    }

    private static int clampByte(float v) {
        return Mth.clamp((int) (v * 255.0F), 0, 255);
    }
}
