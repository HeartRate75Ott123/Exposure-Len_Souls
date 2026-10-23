package com.plumejade.lensouls.ability.client.sky;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import net.minecraft.client.Camera;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * 时间定格 · 冻结天穹（纯客户端天空替换）。
 * <p>
 * 只在<b>触发时停的本地玩家</b>客户端绘制（{@link ClientFreezeCache#isFreezeSource()}），
 * 其他玩家看不到——天空完全由客户端贴图/着色器产出，服务端只广播"谁触发"。
 *
 * <h3>为什么用 AFTER_LEVEL 的深度掩码全屏 pass</h3>
 * <ul>
 *   <li>vanilla 在 {@code LevelRenderer.renderSky} 里画天空，Iris 光影则用 shaderpack 的
 *       {@code gbuffers_skybasic} 覆盖同一批像素，且延迟管线会在 {@code renderLevel} 尾部
 *       （Iris 的 {@code finalizeLevelRendering}）把整幅画面重合成一遍。
 *       ⇒ 在 {@code renderSky} 阶段画的自定义天空，会被 shaderpack 的延迟 sky 覆盖掉。</li>
 *   <li>NeoForge 的 {@code Stage.AFTER_LEVEL} 派发点位于 {@code GameRenderer.renderLevel} 里
 *       {@code LevelRenderer.renderLevel(...)} 返回<b>之后</b>——此时 Iris 已经完成
 *       {@code composite/final} 合成，画面就是最终世界画面；深度缓冲也还是场景深度
 *       （原版的手部渲染清深度发生在这之后）。</li>
 *   <li>把全屏四边形固定在裁剪空间 z = 1.0，开 {@code GL_LEQUAL} 深度测试但不写深度：
 *       只有深度仍是 clear 值 1.0 的像素（= 天空）会通过，地形/实体/半透明方块自然遮挡。
 *       全程不采样深度纹理、不依赖 Iris 反射，原版 / Sodium / Iris 行为一致。</li>
 *   <li>Iris 下不污染渲染管线：此刻 {@code IrisRenderingPipeline.isRenderingWorld == false}，
 *       Iris 不会在我们调用自定义 ShaderInstance 时把 FBO 抢回 gbuffer。</li>
 * </ul>
 * <p>
 * 视野被雾/水/致盲完全遮住时（原版本来就不画天空）一并淡出，避免水下看到星空。
 */
@OnlyIn(Dist.CLIENT)
public final class TimeFreezeSky {

    /** 天空全屏着色器（在 RegisterShadersEvent 中注册）。 */
    public static ShaderInstance shader;

    /** 淡入 / 淡出时长（秒）。 */
    private static final float FADE_IN = 0.32F;
    private static final float FADE_OUT = 0.55F;
    /** 低于该强度就彻底跳过绘制。 */
    private static final float EPSILON = 0.004F;

    /** 表盘抬升角（弧度）：触发瞬间把时计放在视线正前方的地平线上方。 */
    private static final float CLOCK_ELEVATION = (float) Math.toRadians(23.0);

    private static final Vector3f CLOCK_FALLBACK = new Vector3f(0.0F, 0.42F, 1.0F).normalize();

    /** 当前时计圆心方向（世界空间；触发瞬间锁定，之后不随视角移动）。 */
    private static final Vector3f clockDir = new Vector3f(CLOCK_FALLBACK);

    private static float intensity;
    private static long lastNanos;
    /** 本帧是否把时计方向重置为"视线正前方"（淡入起点）。 */
    private static boolean armed;

    private TimeFreezeSky() {
    }

    // =====================================================================
    //  状态
    // =====================================================================

    /** 本地玩家是否应看到冻结天穹（时停中 + 自己是触发者；测试模式放行）。 */
    public static boolean isSource() {
        return ClientFreezeCache.isFreezeSource();
    }

    /** 是否还有残余强度（云层隐藏判断用：淡出期间也算）。 */
    public static boolean isSkyActive() {
        return intensity > EPSILON;
    }

    // =====================================================================
    //  渲染入口
    // =====================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        RenderLevelStageEvent.Stage stage = event.getStage();
        boolean fabulous = mc.options.graphicsMode().get() == GraphicsStatus.FABULOUS;

        // RenderType-shaderpack 的延迟管线会在 renderLevel 尾部整体重合成，
        // 所以主路径放在 AFTER_LEVEL；fabulous（原版多层合成）没有 shaderpack，
        // 在 AFTER_SKY 画更贴近原版的合成顺序。
        boolean drawHere = fabulous ? stage == RenderLevelStageEvent.Stage.AFTER_SKY
                                    : stage == RenderLevelStageEvent.Stage.AFTER_LEVEL;
        if (!drawHere) return;

        float dt = tickDelta();
        advanceIntensity(dt, shouldBeVisible(mc));
        if (intensity <= EPSILON) {
            armed = false;
            return;
        }
        if (shader == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (!armed) {
            captureClockDirection(camera);
            armed = true;
        }

        draw(mc, camera, event.getProjectionMatrix(),
                event.getPartialTick().getGameTimeDeltaPartialTick(false), !fabulous);
    }

    private static float tickDelta() {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1.0E9F;
        lastNanos = now;
        return Mth.clamp(dt, 0.0F, 0.10F);
    }

    private static void advanceIntensity(float dt, boolean visible) {
        float target = visible ? 1.0F : 0.0F;
        if (dt <= 0.0F) {
            if (visible && intensity < target) intensity = Math.max(intensity, EPSILON * 2.0F);
            return;
        }
        if (intensity < target) {
            intensity = Math.min(target, intensity + dt / FADE_IN);
        } else if (intensity > target) {
            intensity = Math.max(target, intensity - dt / FADE_OUT);
        }
    }

    /** 是否该显示：自己触发的时停 + 原版"此刻确实在画天空"的条件。 */
    private static boolean shouldBeVisible(Minecraft mc) {
        if (!isSource()) return false;
        if (mc.player == null || mc.level == null) return false;
        Camera camera = mc.gameRenderer.getMainCamera();
        // 水下 / 岩浆 / 细雪：原版不画天空
        if (camera.getFluidInCamera() != FogType.NONE) return false;
        Vec3 pos = camera.getPosition();
        if (mc.level.effects().isFoggyAt(Mth.floor(pos.x), Mth.floor(pos.z))) return false;
        if (mc.gui.getBossOverlay().shouldCreateWorldFog()) return false;
        if (camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false;
        }
        return true;
    }

    /** 触发瞬间把时计放在视线正前方（保留方位角，抬升到 {@link #CLOCK_ELEVATION}）。 */
    private static void captureClockDirection(Camera camera) {
        Vector3f look = camera.getLookVector();
        float horizontal = Mth.sqrt(look.x() * look.x() + look.z() * look.z());
        if (horizontal < 1.0E-4F) {
            clockDir.set(CLOCK_FALLBACK);
            return;
        }
        float nx = look.x() / horizontal;
        float nz = look.z() / horizontal;
        float cos = Mth.cos(CLOCK_ELEVATION);
        float sin = Mth.sin(CLOCK_ELEVATION);
        clockDir.set(nx * cos, sin, nz * cos).normalize();
    }

    // =====================================================================
    //  绘制
    // =====================================================================

    private static void draw(Minecraft mc, Camera camera, Matrix4f projection, float partialTick, boolean depthTest) {
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) return;

        float tanHalfFov = Math.abs(projection.m11()) > 1.0E-4F ? 1.0F / Math.abs(projection.m11()) : 0.70F;
        float aspect = (float) main.width / (float) Math.max(1, main.height);

        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f right = new Vector3f(camera.getLeftVector()).negate();

        float[] fog = RenderSystem.getShaderFogColor();
        float dayBrightness = dayBrightness(mc, partialTick);

        main.bindWrite(true);

        RenderSystem.assertOnRenderThread();
        RenderSystem.disableCull();
        RenderSystem.disableScissor();
        RenderSystem.depthMask(false);
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(() -> shader);

        uniform1f("SkyTime", wallClockSeconds());
        uniform1f("SkyIntensity", intensity);
        uniform2f("SkyScreenSize", main.width, main.height);
        uniform1f("SkyTanHalfFov", tanHalfFov);
        uniform1f("SkyAspect", aspect);
        uniform1f("SkyDayBrightness", dayBrightness);
        uniform3f("SkyFogColor", fog[0], fog[1], fog[2]);
        uniform3f("SkyCamForward", forward.x(), forward.y(), forward.z());
        uniform3f("SkyCamUp", up.x(), up.y(), up.z());
        uniform3f("SkyCamRight", right.x(), right.y(), right.z());
        uniform3f("SkyClockDir", clockDir.x, clockDir.y, clockDir.z);

        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, -1.0F, 0.0F);
        buffer.addVertex(1.0F, 1.0F, 0.0F);
        buffer.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        // 复位（后续手部 / GUI 依赖这些状态）
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** 墙钟秒（取 1 小时模，保证 float 精度足够驱动指针微颤）。 */
    private static float wallClockSeconds() {
        return (float) ((System.nanoTime() % 3_600_000_000_000L) / 1.0E9);
    }

    /** 白天 ≈ 1.0，午夜 ≈ 0.55；让时停天空在夜里也偏暗，不突兀。 */
    private static float dayBrightness(Minecraft mc, float partialTick) {
        if (mc.level == null) return 1.0F;
        float timeOfDay = mc.level.getTimeOfDay(partialTick);
        // 0 = 日出，0.25 = 正午，0.5 = 日落，0.75 = 午夜
        float sun = Mth.cos((timeOfDay - 0.25F) * Mth.TWO_PI);
        float day = Mth.clamp(0.5F + 0.5F * sun, 0.0F, 1.0F);
        return 0.55F + 0.45F * day;
    }

    private static void uniform1f(String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private static void uniform2f(String name, float a, float b) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(a, b);
    }

    private static void uniform3f(String name, float a, float b, float c) {
        var uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(a, b, c);
    }
}
