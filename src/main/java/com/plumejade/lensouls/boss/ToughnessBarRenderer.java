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

    private static final float POS_SMOOTH = 0.06f;
    /** 碰撞箱顶面与韧性条底部的固定空隙（世界格），避免与高模型头部重叠 */
    private static final double BAR_HEAD_CLEARANCE = 0.5;
    private static float debugProgress = 0.3f;
    private static boolean useDebug = false;
    private static final Map<UUID, Vec3> smoothPos = new ConcurrentHashMap<>();

    public static void setDebugProgress(float p) { debugProgress = Math.max(0, Math.min(1, p)); }
    public static void setUseDebug(boolean u) { useDebug = u; }
    public static float getDebugProgress() { return debugProgress; }

    @SubscribeEvent public static void onLevelLoad(LevelEvent.Load e) {
        if (e.getLevel().isClientSide()) smoothPos.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Camera cam = mc.gameRenderer.getMainCamera();
        var src = mc.renderBuffers().bufferSource();
        VertexConsumer vc = src.getBuffer(getP());
        VertexConsumer nc = src.getBuffer(getN());

        if (useDebug) {
            renderBar(event, mc, cam, mc.player, debugProgress, vc, nc);
        } else {
            for (var entry : BossToughnessClientCache.getEntries()) {
                if (!(mc.level.getEntity(entry.entityId()) instanceof LivingEntity le) || !le.isAlive()) continue;
                renderBar(event, mc, cam, le, entry.progress(), vc, nc);
            }
        }

        src.endBatch(getP());
        src.endBatch(getN());
    }

    private static void renderBar(RenderLevelStageEvent event, Minecraft mc, Camera cam, LivingEntity target, float progress, VertexConsumer vc, VertexConsumer nc) {
        // 基准改为碰撞箱顶面（bb.maxY），向上留固定空隙后叠加配置偏移，不再贴模型头部
        Vec3 raw = new Vec3(target.getX(),
                target.getBoundingBox().maxY + BAR_HEAD_CLEARANCE + Config.TOUGH_BAR_VERTICAL_OFFSET.get(),
                target.getZ());
        UUID uid = target.getUUID();
        float speed = target.hurtTime > 0 ? POS_SMOOTH * 0.5f : POS_SMOOTH;
        Vec3 pos = smoothPos.compute(uid, (k, v) -> {
            if (v == null) return raw;
            return new Vec3(v.x + (raw.x - v.x) * speed, v.y + (raw.y - v.y) * speed, v.z + (raw.z - v.z) * speed);
        });

        int w = Config.TOUGH_BAR_WIDTH.get(), h = Config.TOUGH_BAR_HEIGHT.get();
        var ps = event.getPoseStack();

        ps.pushPose();
        ps.translate(pos.x - cam.getPosition().x, pos.y - cam.getPosition().y, pos.z - cam.getPosition().z);
        ps.mulPose(cam.rotation());
        ps.scale(-0.025f * w / 32f, -0.025f * h / 32f, 0.025f);
        Matrix4f mat = ps.last().pose();
        float hw = w / 2f, hh = h / 2f;
        float split = h * (1f - progress);

        vc.addVertex(mat, -hw, -hh + split, 0).setUv(0, 1f - progress).setColor(255, 255, 255, 255);
        vc.addVertex(mat, hw, -hh + split, 0).setUv(1, 1f - progress).setColor(255, 255, 255, 255);
        vc.addVertex(mat, hw, -hh, 0).setUv(1, 0).setColor(255, 255, 255, 255);
        vc.addVertex(mat, -hw, -hh, 0).setUv(0, 0).setColor(255, 255, 255, 255);

        if (progress > 0.001f) {
            nc.addVertex(mat, -hw, hh, 0).setUv(0, 1).setColor(255, 255, 255, 255);
            nc.addVertex(mat, hw, hh, 0).setUv(1, 1).setColor(255, 255, 255, 255);
            nc.addVertex(mat, hw, -hh + split, 0).setUv(1, 1f - progress).setColor(255, 255, 255, 255);
            nc.addVertex(mat, -hw, -hh + split, 0).setUv(0, 1f - progress).setColor(255, 255, 255, 255);
        }

        ps.popPose();
    }
}
