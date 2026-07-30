package com.plumejade.lensouls.boss;

import com.mojang.blaze3d.vertex.*;
import com.plumejade.lensouls.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ToughnessBarRenderer {

    private static final ResourceLocation PROTECTED =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/entity/toughness_bar/protected.png");
    private static final ResourceLocation NO_PROTECTION =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/entity/toughness_bar/no_protection.png");

    // POSITION_TEX_COLOR + TRANSLUCENT：无 UV1/UV2/Normal，无光照，无发黑
    private static RenderType rtP = null, rtN = null;
    private static RenderType make(ResourceLocation tex, String name) {
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                        .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(tex, false, false))
                        .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                        .setDepthTestState(net.minecraft.client.renderer.RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_DEPTH_WRITE)
                        .createCompositeState(false));
    }
    private static RenderType getP() { if (rtP == null) rtP = make(PROTECTED, "lensouls_bar_p"); return rtP; }
    private static RenderType getN() { if (rtN == null) rtN = make(NO_PROTECTION, "lensouls_bar_n"); return rtN; }

    private static final float POS_DELAY = 0.08f, POS_SMOOTH = 0.20f;
    private static final float PROGRESS_SPEED = 0.12f;
    private static float debugProgress = 0.3f;
    private static boolean useDebug = false;
    private static final Map<UUID, Vec3> delayPos = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> smoothPos = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> smoothProgress = new ConcurrentHashMap<>();

    public static void setDebugProgress(float p) { debugProgress = Math.max(0, Math.min(1, p)); }
    public static void setUseDebug(boolean u) { useDebug = u; }
    public static float getDebugProgress() { return debugProgress; }

    @SubscribeEvent public static void onLevelLoad(LevelEvent.Load e) {
        if (e.getLevel().isClientSide()) { delayPos.clear(); smoothPos.clear(); }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Camera cam = mc.gameRenderer.getMainCamera();

        // 调试模式：只渲染玩家头顶
        if (useDebug) {
            renderBar(event, mc, cam, mc.player, debugProgress);
            return;
        }

        // 遍历所有 BOSS 实体，每个渲染独立的韧性条
        for (var entry : BossToughnessClientCache.getEntries()) {
            if (!(mc.level.getEntity(entry.entityId()) instanceof LivingEntity le) || !le.isAlive()) continue;
            renderBar(event, mc, cam, le, entry.progress());
        }
    }

    /** 在单个实体头顶渲染韧性条 */
    private static void renderBar(RenderLevelStageEvent event, Minecraft mc, Camera cam, LivingEntity target, float progress) {
        Vec3 raw = target.position().add(0, target.getBbHeight() + Config.TOUGH_BAR_VERTICAL_OFFSET.get(), 0);
        UUID uid = target.getUUID();
        Vec3 d = delayPos.get(uid);
        if (d == null) d = raw;
        else d = new Vec3(d.x + (raw.x - d.x) * POS_DELAY, d.y + (raw.y - d.y) * POS_DELAY, d.z + (raw.z - d.z) * POS_DELAY);
        delayPos.put(uid, d);
        Vec3 s = smoothPos.get(uid);
        if (s == null) s = d;
        else s = new Vec3(s.x + (d.x - s.x) * POS_SMOOTH, d.y + (d.y - s.y) * POS_SMOOTH, d.z + (d.z - s.z) * POS_SMOOTH);
        smoothPos.put(uid, s);

        float sp = smoothProgress.getOrDefault(uid, progress);
        sp += (progress - sp) * PROGRESS_SPEED;
        if (Math.abs(sp - progress) < 0.002f) sp = progress;
        smoothProgress.put(uid, sp);
        progress = sp;

        if (delayPos.size() > 200) { delayPos.clear(); smoothPos.clear(); smoothProgress.clear(); }

        int w = Config.TOUGH_BAR_WIDTH.get(), h = Config.TOUGH_BAR_HEIGHT.get();
        var ps = event.getPoseStack();
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();

        ps.pushPose();
        ps.translate(s.x - cam.getPosition().x, s.y - cam.getPosition().y, s.z - cam.getPosition().z);
        ps.mulPose(cam.rotation());
        ps.scale(-0.025f * w / 32f, -0.025f * h / 32f, 0.025f);
        Matrix4f mat = ps.last().pose();
        float hw = w / 2f, hh = h / 2f;
        float split = h * (1f - progress); // 分界线（不重叠，无混合闪烁）

        // 底层 protected（下部）
        VertexConsumer vc = src.getBuffer(getP());
        vc.addVertex(mat, -hw, -hh + split, 0).setUv(0, 1f - progress).setColor(255, 255, 255, 255);
        vc.addVertex(mat, hw, -hh + split, 0).setUv(1, 1f - progress).setColor(255, 255, 255, 255);
        vc.addVertex(mat, hw, -hh, 0).setUv(1, 0).setColor(255, 255, 255, 255);
        vc.addVertex(mat, -hw, -hh, 0).setUv(0, 0).setColor(255, 255, 255, 255);
        src.endBatch(getP());

        if (progress > 0.001f) {
            // 顶层 no_protection（上部）
            VertexConsumer nc = src.getBuffer(getN());
            nc.addVertex(mat, -hw, hh, 0).setUv(0, 1).setColor(255, 255, 255, 255);
            nc.addVertex(mat, hw, hh, 0).setUv(1, 1).setColor(255, 255, 255, 255);
            nc.addVertex(mat, hw, -hh + split, 0).setUv(1, 1f - progress).setColor(255, 255, 255, 255);
            nc.addVertex(mat, -hw, -hh + split, 0).setUv(0, 1f - progress).setColor(255, 255, 255, 255);
            src.endBatch(getN());
        }

        ps.popPose();
    }
}
