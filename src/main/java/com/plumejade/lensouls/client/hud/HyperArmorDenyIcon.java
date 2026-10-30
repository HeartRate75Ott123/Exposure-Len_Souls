package com.plumejade.lensouls.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.plumejade.lensouls.client.feather.RoundedRect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 「白霸体挡下削韧」时的<b>默认</b>反馈：屏幕上方正中一枚 18×18 的<b>原版虚弱图标</b>
 * （{@code minecraft:textures/mob_effect/weakness.png}）。
 *
 * <h3>它和 {@link VitalDenyOverlay} 的分工</h3>
 * 同一件事（白霸体挡下、且这次没有任何一只真吃到削韧）有两种画法，服务端在
 * {@code VitalStrikeRejectPacket} 里一次定死：
 * <ul>
 *   <li>相机<b>闪光灯槽装着「机械眼」</b>且不是那四个静默能力 → 大故障叉号（{@link VitalDenyOverlay}）；</li>
 *   <li>其余情况（含没装机械眼、拍立得、四个静默能力）→ 就是这里的小图标。</li>
 * </ul>
 * 触发口径两者<b>完全一致</b>（用户定：只要有白霸体挡下、且这一轮没有真正削到，就弹），
 * 所以这里不做任何自己的条件判断，只负责"收到信号就画一段"。
 *
 * <h3>位置 / 大小</h3>
 * <ul>
 *   <li>横向居中；纵向 {@link #CENTER_Y_RATIO} = 0.185 屏高 —— 按用户截图红框量出来的比例
 *       （正好与取景框底部那个冷却禁止符 0.815 关于中心镜像）。</li>
 *   <li>18×18 GUI 像素：与原版效果图标同尺寸，也与冷却禁止符（直径 18px）一致。</li>
 *   <li>背后垫一个很淡的深色圆盘（与 {@link ViewfinderOverlay} 的禁止符同族）：那个位置常是天/雪/太阳，
 *       原版图标是半透明底上的灰白小图，不垫底会糊。</li>
 * </ul>
 *
 * <h3>时序（用户定稿：0.66s，要有淡入淡出）</h3>
 * 淡入 {@link #FADE_IN_MS}（平方缓入）→ 满不透明 {@link #HOLD_MS} → 淡出 {@link #FADE_OUT_MS}
 * （平方缓出，尾巴收得快），合计 {@link #TOTAL_MS} = <b>0.66s</b>，与大故障叉号同一时长。
 *
 * <h3>连续触发：合并成一段，刷新时长</h3>
 * 重复触发<b>不会叠出第二枚</b>，只把这一段续上（{@link #show()}）。续的方式是"从当前不透明度
 * 对应的淡入时刻重新计时"——所以满不透明时再触发不会先掉回 0 再抬起来（那会闪一下），
 * 正在淡出时再触发则是从当前浓度平滑地重新抬回满。
 *
 * <h3>何时不画</h3>
 * 由 {@code GameRendererFrameEndMixin} 在 {@code GuiGraphics.flush()} 之后调用（与另两个叠加层同一注入点，
 * 才能压在最上层、且 Exposure 取景器 cancel 掉 {@code Gui.render} 时仍然可见）。
 * 开着任何 Screen、或正在后台抓帧出片时一律跳过（不污染照片）。
 */
@OnlyIn(Dist.CLIENT)
public final class HyperArmorDenyIcon {

    /** 原版虚弱效果图标（18×18，透明底） */
    private static final ResourceLocation WEAKNESS_ICON =
            ResourceLocation.withDefaultNamespace("textures/mob_effect/weakness.png");

    /** 淡入时长（平方缓入：前几帧几乎看不见，末尾快速抬满） */
    private static final long FADE_IN_MS = 120L;
    /** 满不透明保持 */
    private static final long HOLD_MS = 280L;
    /** 淡出时长（平方缓出） */
    private static final long FADE_OUT_MS = 260L;
    /** 整段 = 0.12 + 0.28 + 0.26 = 0.66s（与大故障叉号 {@link VitalDenyOverlay} 的 0.66s 对齐） */
    private static final long TOTAL_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS;

    /** 竖直位置（占屏幕高度比例）= 用户截图红框的 0.185 屏高 */
    private static final float CENTER_Y_RATIO = 0.185F;
    /** 图标边长（GUI 像素），与原版效果图标、冷却禁止符同尺寸 */
    private static final int ICON_SIZE = 18;
    /** 衬底圆盘半径（= 半边长 ⇒ 正圆） */
    private static final int DISC_RADIUS = 12;

    /** 禁止符同款深玻璃圆盘的两色 */
    private static final int DISC_TOP = 0x141A22;
    private static final int DISC_BOTTOM = 0x0E1218;

    private static long startedAt = -1L;

    private HyperArmorDenyIcon() {
    }

    /**
     * 触发一次。<b>连续多次触发合并成一段</b>：不叠出第二枚，只把这一段续长。
     * <p>
     * 续法：把起点往前挪到"当前不透明度所对应的淡入时刻"，于是曲线从当前浓度接着往上走，
     * 满不透明时再触发就是原地继续满着（不会闪一下 0）。
     */
    public static void show() {
        long now = System.currentTimeMillis();
        float current = currentAlpha(now);
        long offset = (long) (FADE_IN_MS * Mth.sqrt(Mth.clamp(current, 0.0F, 1.0F)));
        startedAt = now - offset;
    }

    private static float currentAlpha(long now) {
        if (startedAt < 0L) return 0.0F;
        long elapsed = now - startedAt;
        if (elapsed < 0L || elapsed >= TOTAL_MS) return 0.0F;
        return alphaAt(elapsed);
    }

    public static void render() {
        if (startedAt < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.noRender) return;

        long elapsed = System.currentTimeMillis() - startedAt;
        if (elapsed < 0L || elapsed >= TOTAL_MS) {
            startedAt = -1L;
            return;
        }
        // 开界面 / 后台抓帧出片时都不画（那张照片里不该出现这枚图标）
        if (mc.screen != null) return;
        if (io.github.mortuusars.exposure.client.capture.task.BackgroundScreenshotCaptureTask.isCapturing()) {
            startedAt = -1L;
            return;
        }

        float alpha = alphaAt(elapsed);
        if (alpha <= 0.01F) return;

        GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            draw(graphics, width / 2, Math.round(height * CENTER_Y_RATIO), alpha);
        } finally {
            graphics.flush();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    /** 时间曲线：平方缓入 → 满 → 平方缓出 */
    private static float alphaAt(long elapsed) {
        if (elapsed < FADE_IN_MS) {
            float k = elapsed / (float) FADE_IN_MS;
            return k * k;
        }
        if (elapsed < FADE_IN_MS + HOLD_MS) return 1.0F;
        float k = (elapsed - FADE_IN_MS - HOLD_MS) / (float) FADE_OUT_MS;
        return Math.max(0.0F, 1.0F - k * k);
    }

    private static void draw(GuiGraphics graphics, int cx, int cy, float a) {
        // 底衬：圆盘（不透明度约 0.55，几乎看不出是个盘，只负责把图标从亮背景里择出来）
        int discAlpha = clampByte(a * 0.55F);
        RoundedRect.roundedRect(graphics,
                cx - DISC_RADIUS, cy - DISC_RADIUS, DISC_RADIUS * 2, DISC_RADIUS * 2, DISC_RADIUS,
                (discAlpha << 24) | DISC_TOP, (discAlpha << 24) | DISC_BOTTOM, 0, 0.0F);

        // 原版虚弱图标
        graphics.setColor(1.0F, 1.0F, 1.0F, a);
        graphics.blit(WEAKNESS_ICON,
                cx - ICON_SIZE / 2, cy - ICON_SIZE / 2,
                0, 0,
                ICON_SIZE, ICON_SIZE,
                ICON_SIZE, ICON_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static int clampByte(float v) {
        return Mth.clamp((int) (v * 255.0F), 0, 255);
    }
}
