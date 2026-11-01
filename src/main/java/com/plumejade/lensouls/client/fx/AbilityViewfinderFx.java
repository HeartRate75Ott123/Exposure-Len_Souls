package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL11;

/**
 * 能力取景框色晕（纯客户端，最后一道全屏 pass）。
 *
 * <h3>为什么是一个独立的 pass</h3>
 * 需求是「盖在 Exposure 取景框上层」。Exposure 的取景框在 {@code Gui.render} 的 HEAD 画，
 * 而 {@code ScreenFxManager} 那条屏幕后处理挂在 {@code GameRenderer.renderLevel} 的 RETURN
 * （世界 + 手部之后、HUD 之前）—— 它<b>盖不住取景框</b>。所以这一层改画在
 * 「vanilla 把 HUD / 屏幕 / toast 全部画完、GUI 正交矩阵还没弹出的那一次
 * {@code GuiGraphics.flush()} 之后」（{@code GameRendererFrameEndMixin} 的 hudTopOverlay 注入点），
 * 时机天然是整帧最上层。
 *
 * <h3>画什么</h3>
 * 不做任何采样：{@code ability_viewfinder.fsh} 只输出一层 alpha 色晕 ——
 * <b>整屏椭圆羽化</b>：过渡窗口横跨整个半径（shader 里的 FEATHER_IN..FEATHER_OUT），
 * 中心几乎为 0、越往外越浓、四角最浓；不做画幅矩形裁切（那会变成两条平色带）。
 * 前沿由外向内收。颜色按当前能力取自能力球贴图
 * （{@link #tintOf(AbilityType)}，实测值写在注释里），所以「能力窃取 = 品红、时间定格 = 青」
 * 是随贴图走的，不是另拍一套配色。
 *
 * <h3>入场</h3>
 * 两个通道同时走、速度不同：{@code alpha}（整体由淡到浓，0.50s）与 {@code reach}
 * （前沿由外向内推进，0.85s）。单一通道一次拉满会很硬 —— 前沿落后于浓度，
 * 看到的是「色雾慢慢漫进来」而不是「一层面板掉下来」。退场同理（0.32s / 0.45s）。
 */
@OnlyIn(Dist.CLIENT)
public final class AbilityViewfinderFx {

    /** 取景框色晕着色器（RegisterShadersEvent 注册） */
    public static ShaderInstance shader;

    /** 入场：整体不透明度爬升秒数 */
    private static final float FADE_IN = 0.50F;
    /** 退场：整体不透明度回落秒数 */
    private static final float FADE_OUT = 0.32F;
    /** 入场：前沿推进秒数（刻意比淡入慢，两层不同步才柔） */
    private static final float REACH_IN = 0.85F;
    /** 退场：前沿回退秒数 */
    private static final float REACH_OUT = 0.45F;
    /** 换能力时两色之间的插值速度（1/秒） */
    private static final float COLOR_SPEED = 6.0F;

    /** 椭圆半轴（uv：0.5 = 半屏）——整屏椭圆羽化，四边都带色、四角最浓 */
    private static final float ELLIPSE_U = 0.62F;
    private static final float ELLIPSE_V = 0.74F;

    /** 当前不透明度 0..1（淡入淡出的主通道） */
    private static float alpha;
    /** 当前前沿 0..1（0 = 只贴最外圈，1 = 推到画面内侧） */
    private static float reach;
    /** 当前颜色（sRGB 0..1），换能力时向目标色插值 */
    private static float curR, curG, curB;
    private static float tgtR, tgtG, tgtB;
    private static boolean colorInit;

    private static long lastNanos;
    private static float time;
    /** 本次激活是否已经打过日志（只打一次，避免刷屏） */
    private static boolean loggedOpen;
    /** shader 为空是否已经报过（每会话一次） */
    private static boolean loggedMissing;

    /**
     * 外部指定色（让别的系统也能复用同一层色晕）。
     * 传 {@code null} = 回到「按能力球贴图取色」。
     */
    private static float[] colorOverride;

    private AbilityViewfinderFx() {
    }

    /** 换存档 / 断线时复位（否则下次进世界会带着上次的颜色和透明度淡入）。 */
    public static void reset() {
        alpha = 0.0F;
        reach = 0.0F;
        colorInit = false;
        lastNanos = 0L;
        time = 0.0F;
        loggedOpen = false;
        loggedMissing = false;
        colorOverride = null;
    }

    /**
     * 支持传入任意颜色值（0..1 sRGB）。{@link #clearColorOverride()} 恢复按能力取色。
     */
    public static void setColorOverride(float r, float g, float b) {
        colorOverride = new float[]{clamp01(r), clamp01(g), clamp01(b)};
    }

    public static void clearColorOverride() {
        colorOverride = null;
    }

    /** 这一层此刻是否还看得见（供其它 HUD 判断要不要避让）。 */
    public static boolean isVisible() {
        return alpha > 0.001F || reach > 0.001F;
    }

    // =====================================================================
    //  渲染（由 GameRendererFrameEndMixin 在 GuiGraphics.flush() 之后调用）
    // =====================================================================

    public static void render() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (!RenderSystem.isOnRenderThread()) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.1F);
        lastNanos = now;
        time += dt;

        AbilityType ability = activeAbility(mc);
        boolean open = ability != null;

        if (open) {
            float[] tint = colorOverride != null ? colorOverride : tintOf(ability);
            tgtR = tint[0];
            tgtG = tint[1];
            tgtB = tint[2];
        }

        // 颜色插值：取景框里滚轮换能力时是「渐变过去」，不是一帧换色
        float k = colorInit ? Math.min(1.0F, dt * COLOR_SPEED) : 1.0F;
        curR += (tgtR - curR) * k;
        curG += (tgtG - curG) * k;
        curB += (tgtB - curB) * k;
        colorInit = true;

        // 两个通道各自缓动：浓度快、前沿慢（入场）；退场反过来更利落
        alpha = approach(alpha, open ? 1.0F : 0.0F, dt / (open ? FADE_IN : FADE_OUT));
        reach = approach(reach, open ? 1.0F : 0.0F, dt / (open ? REACH_IN : REACH_OUT));

        if (open && !loggedOpen) {
            loggedOpen = true;
            LenSouls.LOGGER.info("[AbilityFx] 取景框色晕激活 ability={} tint=#{}",
                    ability.getId(), hex(curR, curG, curB));
        }

        if (!isVisible()) {
            if (loggedOpen) {
                loggedOpen = false;
                LenSouls.LOGGER.info("[AbilityFx] 取景框色晕已退出");
            }
            return;
        }

        if (shader == null) {
            if (!loggedMissing) {
                loggedMissing = true;
                LenSouls.LOGGER.warn("[AbilityFx] ability_viewfinder 着色器未加载，色晕跳过");
            }
            return;
        }

        draw(mc);
    }

    private static void draw(Minecraft mc) {
        var main = mc.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;

        // HUD 之后主帧缓冲仍是当前目标，bindWrite 只是把 viewport 摆正（不清屏）
        main.bindWrite(true);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        set2f("FxScreenSize", main.width, main.height);
        set1f("FxTime", time);
        set3f("FxColor", curR, curG, curB);
        set1f("FxAlpha", alpha);
        set1f("FxReach", reach);
        set2f("FxEllipse", ELLIPSE_U, ELLIPSE_V);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // =====================================================================
    //  触发条件
    // =====================================================================

    /**
     * 当前该不该亮、亮哪个能力：正在看取景器（Exposure 自己那套判定）+ 手持相机
     * 记录了选中能力 + 没有打开控制屏幕（相机设置界面是 GUI，不该被色晕盖住）。
     */
    private static AbilityType activeAbility(Minecraft mc) {
        // 隐藏 HUD（F1）时也不画：那种截图要干净
        if (mc.options.hideGui) return null;
        if (mc.screen != null) return null;
        if (!viewfinderActive()) return null;
        AbilityType type = CameraAbilityStore.getSelectedType(mc.player.getMainHandItem());
        if (type == null) type = CameraAbilityStore.getSelectedType(mc.player.getOffhandItem());
        return type;
    }

    /**
     * 是否正在通过取景器看 —— Exposure 的取景界面不是 {@code Screen}，只能问它自己；
     * 判定口径与 {@code ViewfinderOverlay} / Exposure 的 {@code GuiMixin} 一致。
     */
    private static boolean viewfinderActive() {
        try {
            var viewfinder = io.github.mortuusars.exposure.client.camera.CameraClient.viewfinder();
            return viewfinder != null && viewfinder.isLookingThrough();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // =====================================================================
    //  能力 → 颜色
    // =====================================================================

    /**
     * 能力球贴图的主色。
     * <p>
     * 取 {@code assets/lensouls/textures/item/skill_ball/*.png}（16×16 玻璃球，带高光）里
     * 「alpha × 饱和度」加权的平均色 —— 直接算术平均会被高光冲淡成灰白。
     * 括号里是<b>贴图文件名</b>：两个历史文件名和能力 id 不一致（能力窃取 =
     * {@code ability_leech.png}、断魂 = {@code life_kill.png}），别按 id 猜。
     * <b>换能力球贴图要回头同步这里</b>。
     */
    private static float[] tintOf(AbilityType type) {
        return switch (type) {
            case WEAKNESS_LENS   -> rgb(0x347C7C); // weakness_lens.png
            case SPATIAL_WARP    -> rgb(0xFF9B10); // spatial_warp.png
            case TEMPORAL_RECALL -> rgb(0x52BA1B); // temporal_recall.png
            case TIME_STOP       -> rgb(0x2BAEFE); // time_stop.png
            case VITAL_STRIKE    -> rgb(0x9E1212); // vital_strike.png
            case SOUL_SEVER      -> rgb(0x56357C); // life_kill.png
            case ABILITY_STEAL   -> rgb(0xE130B8); // ability_leech.png（能力窃取：品红 / 粉）
            case WILD_GLIMPSE    -> rgb(0x7C5A34); // wild_glimpse.png
        };
    }

    private static float[] rgb(int hex) {
        return new float[]{
                ((hex >> 16) & 0xFF) / 255.0F,
                ((hex >> 8) & 0xFF) / 255.0F,
                (hex & 0xFF) / 255.0F
        };
    }

    private static String hex(float r, float g, float b) {
        return String.format("%02X%02X%02X",
                Math.round(clamp01(r) * 255.0F),
                Math.round(clamp01(g) * 255.0F),
                Math.round(clamp01(b) * 255.0F));
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : (v > 1.0F ? 1.0F : v);
    }

    private static float approach(float value, float target, float step) {
        if (step <= 0.0F) return value;
        if (value < target) return Math.min(target, value + step);
        return Math.max(target, value - step);
    }

    private static void set1f(String name, float v) {
        var u = shader.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set2f(String name, float a, float b) {
        var u = shader.getUniform(name);
        if (u != null) u.set(a, b);
    }

    private static void set3f(String name, float a, float b, float c) {
        var u = shader.getUniform(name);
        if (u != null) u.set(a, b, c);
    }
}
