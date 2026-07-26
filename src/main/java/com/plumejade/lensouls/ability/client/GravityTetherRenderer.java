package com.plumejade.lensouls.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.plumejade.lensouls.entity.GravityBulletEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 引力枪牵引磁力闪电弧渲染器。
 * <p>
 * 使用原版 {@code rendertype_lightning} 着色器（Iris 原生兼容），
 * 端点位置经延迟平滑（类似韧性条），消除位移卡顿感。
 */
public class GravityTetherRenderer {

    private static final int ARC_SEGMENTS = 12;

    /** 位置延迟平滑系数（越小越滞后越滑） */
    private static final float POS_DELAY = 0.06f;

    /** 每颗子弹的平滑位置缓存 */
    private static final Map<Integer, Vec3> smoothFromMap = new HashMap<>();
    private static final Map<Integer, Vec3> smoothToMap = new HashMap<>();

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) return;

        List<GravityBulletEntity> bullets = level.getEntitiesOfClass(
                GravityBulletEntity.class,
                AABB.ofSize(player.position(), 100, 100, 100),
                b -> b.getClientHitTarget() != null
        );

        if (bullets.isEmpty()) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer arcConsumer = bufferSource.getBuffer(GravityTetherRenderTypes.lightningArc());

        float time = (player.tickCount + event.getPartialTick().getGameTimeDeltaTicks()) * 0.15f;

        // 清理已消失子弹的缓存
        smoothFromMap.keySet().removeIf(id -> level.getEntity(id) == null);

        for (GravityBulletEntity bullet : bullets) {
            LivingEntity target = bullet.getClientHitTarget();
            if (target == null || !target.isAlive()) continue;
            Entity owner = bullet.getOwner();
            if (!(owner instanceof LivingEntity le) || !le.isAlive()) continue;

            // ── 原始位置（玩家/被牵引实体腰部） ──
            Vec3 rawFrom = le.position().add(0, le.getBbHeight() * 0.5, 0);
            Vec3 rawTo   = target.position().add(0, target.getBbHeight() * 0.5, 0);

            int bid = bullet.getId();

            // ── 延迟平滑（类似 ToughnessBarRenderer.DELAY） ──
            if (le.hurtTime > 0 || target.hurtTime > 0) {
                // 受伤时加大延迟，进一步消除抖动
                smoothFromMap.put(bid, smoothFromMap.getOrDefault(bid, rawFrom).add(
                        rawFrom.subtract(smoothFromMap.getOrDefault(bid, rawFrom)).scale(POS_DELAY * 0.5f)));
                smoothToMap.put(bid, smoothToMap.getOrDefault(bid, rawTo).add(
                        rawTo.subtract(smoothToMap.getOrDefault(bid, rawTo)).scale(POS_DELAY * 0.5f)));
            } else {
                smoothFromMap.put(bid, smoothFromMap.getOrDefault(bid, rawFrom).add(
                        rawFrom.subtract(smoothFromMap.getOrDefault(bid, rawFrom)).scale(POS_DELAY)));
                smoothToMap.put(bid, smoothToMap.getOrDefault(bid, rawTo).add(
                        rawTo.subtract(smoothToMap.getOrDefault(bid, rawTo)).scale(POS_DELAY)));
            }

            Vec3 from = smoothFromMap.get(bid);
            Vec3 to   = smoothToMap.get(bid);

            float dist = (float) from.distanceTo(to);
            if (dist < 0.5f) continue;

            long baseSeed = bid * 7919L;

            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
            var pose = poseStack.last().pose();

            // ── 单条纯净白色闪电弧 ──
            drawLightningArc(pose, arcConsumer, from, to, baseSeed, time);

            poseStack.popPose();
        }

        bufferSource.endBatch(GravityTetherRenderTypes.lightningArc());
    }

    /**
     * 正弦组合连续噪声（-1 ~ 1）。
     */
    private static float smoothZigzag(float t, long seed, float time) {
        double s = seed;
        double v =
              Math.sin(t * 15.37 + time * 0.25 + s * 0.123) * 0.45
            + Math.sin(t *  7.31 + time * 0.45 + s * 0.456) * 0.28
            + Math.sin(t *  3.17 + time * 0.30 + s * 0.789) * 0.17
            + Math.sin(t * 11.53 + time * 0.55 + s * 0.321) * 0.10;
        return (float) v;
    }

    /**
     * 绘制单条闪电弧（TRIANGLE_STRIP 带状），纯净蓝白色，无分支无扩散。
     */
    private static void drawLightningArc(org.joml.Matrix4f pose, VertexConsumer consumer,
                                          Vec3 from, Vec3 to, long seed, float time) {
        int segments = ARC_SEGMENTS;
        float arcLen = (float) from.distanceTo(to);
        float maxOffset = arcLen * 0.07f;

        Vec3 arcDir = to.subtract(from);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 normal = arcDir.cross(up).normalize();
        if (normal.lengthSqr() < 0.01) normal = new Vec3(1, 0, 0);

        float[] offsets = new float[segments + 1];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            offsets[i] = (float) Math.sin(t * Math.PI) * maxOffset * smoothZigzag(t, seed, time);
        }

        float width = 0.12f;

        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            Vec3 basePos = from.add(arcDir.scale(t));
            Vec3 pos = basePos.add(normal.scale(offsets[i]));

            // 切线方向
            int iPrev = Math.max(0, i - 1);
            int iNext = Math.min(segments, i + 1);
            Vec3 prev = from.add(arcDir.scale((float) iPrev / segments)).add(normal.scale(offsets[iPrev]));
            Vec3 next = from.add(arcDir.scale((float) iNext / segments)).add(normal.scale(offsets[iNext]));
            Vec3 dir = next.subtract(prev).normalize();
            if (dir.lengthSqr() < 0.01) dir = arcDir.normalize();

            Vec3 rightVec = dir.cross(up).normalize();
            if (rightVec.lengthSqr() < 0.01) rightVec = new Vec3(1, 0, 0);

            // ── 纯天蓝色 ──
            int r = 120;
            int g = 200;
            int b = 255;
            int arcAlpha = 220;

            Vec3 leftPos  = pos.add(rightVec.scale(-width));
            Vec3 rightPos = pos.add(rightVec.scale( width));

            consumer.addVertex(pose, (float) leftPos.x, (float) leftPos.y, (float) leftPos.z)
                    .setColor(r, g, b, arcAlpha);
            consumer.addVertex(pose, (float) rightPos.x, (float) rightPos.y, (float) rightPos.z)
                    .setColor(r, g, b, arcAlpha);
        }
    }

}
