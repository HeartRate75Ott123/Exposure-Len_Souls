package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.client.fx.WorldFx;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 空间扭曲「操作场」：一张通电铁丝网。
 * <p>
 * 经纬线网格（14 经 × 16 段 + 6 纬环）用发光细缎带绘制，由 {@code fx_ribbon} 叠上
 * <b>沿网格奔跑的金黄色电流</b>（UV 柔边 + 亮包 + 高频闪烁 + 白芯）。
 * 玩家进到球内时整体亮一档，方便观察内部。
 * <p>
 * 绘制时机见 {@link com.plumejade.lensouls.client.fx.WorldFxPass}（AFTER_LEVEL，Iris 兼容）。
 */
public class SpatialWarpOutlineRenderer {

    private static final int LONGITUDES = 14;
    private static final int LAT_SEGMENTS = 16;
    private static final int LATITUDES = 6;

    private static final float INSIDE_BOOST = 1.45f;

    public static boolean wantsRender() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Minecraft.getInstance().level == null) return false;
        if (!ClientAbilityCache.isSpatialWarpActive()) return false;
        Vec3 center = ClientAbilityCache.getWarpCenter();
        if (center == null) return false;
        String dim = ClientAbilityCache.getWarpDimension();
        return dim != null && dim.equals(player.level().dimension().location().toString());
    }

    public static void render(Vec3 camPos, Matrix4f identity, float time) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        Vec3 center = ClientAbilityCache.getWarpCenter();
        if (center == null) return;

        double range = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        if (range <= 0.5) return;
        float radius = (float) range;
        boolean inside = player.position().distanceTo(center) <= range;
        float boost = inside ? INSIDE_BOOST : 1.0f;

        Vector3f origin = new Vector3f(
                (float) (center.x - camPos.x),
                (float) (center.y - camPos.y),
                (float) (center.z - camPos.z));

        int alpha = clamp(150 * boost);
        int alphaDim = clamp(95 * boost);

        WorldFx.Params params = WorldFx.params();
        params.time = time;
        params.pulseSpeed = 0.42f;
        params.pulseCount = 1.0f;
        params.pulseSharp = 0.045f;
        params.edgeSoft = 1.25f;
        params.coreBoost = 1.25f;
        params.flicker = 0.55f;

        BufferBuilder buffer = WorldFx.begin(params);
        drawWireMesh(buffer, identity, origin, radius, alpha, alphaDim);
        drawNodes(buffer, identity, origin, radius, boost, time);
        drawGyroRings(buffer, identity, origin, radius, boost, time);
        WorldFx.flush(buffer);
    }

    // =====================================================================
    //  几何
    // =====================================================================

    private static void drawWireMesh(BufferBuilder b, Matrix4f pose, Vector3f origin, float radius,
                                     int alpha, int alphaDim) {
        float halfWidth = Math.max(0.018f, radius * 0.0078f);   // 加粗：细线在远处会断断续续

        for (int lon = 0; lon < LONGITUDES; lon++) {
            float theta = (float) (Math.PI * 2.0 * lon / LONGITUDES);
            Vector3f[] pts = new Vector3f[LAT_SEGMENTS + 1];
            Vector3f[] rights = new Vector3f[LAT_SEGMENTS + 1];
            for (int i = 0; i <= LAT_SEGMENTS; i++) {
                double phi = Math.PI * i / LAT_SEGMENTS;
                Vector3f unit = new Vector3f(
                        (float) (Math.sin(phi) * Math.cos(theta)),
                        (float) Math.cos(phi),
                        (float) (Math.sin(phi) * Math.sin(theta)));
                pts[i] = new Vector3f(unit).mul(radius).add(origin);
                Vector3f tangent = new Vector3f(
                        (float) (Math.cos(phi) * Math.cos(theta)),
                        (float) -Math.sin(phi),
                        (float) (Math.cos(phi) * Math.sin(theta)));
                Vector3f r = tangent.cross(unit);
                rights[i] = r.lengthSquared() < 1.0E-8f ? null : r.normalize();
            }
            WorldFx.polyline(b, pose, pts, rights, halfWidth, 255, 198, 92,
                    (lon % 2 == 0) ? alpha : alphaDim);
        }

        for (int lat = 1; lat <= LATITUDES; lat++) {
            double phi = Math.PI * lat / (LATITUDES + 1.0);
            float ringY = (float) (radius * Math.cos(phi));
            float ringR = (float) (radius * Math.sin(phi));
            int segments = 40;
            Vector3f[] pts = new Vector3f[segments + 1];
            Vector3f[] rights = new Vector3f[segments + 1];
            for (int i = 0; i <= segments; i++) {
                double t = Math.PI * 2.0 * i / segments;
                Vector3f p = new Vector3f((float) (Math.cos(t) * ringR), ringY, (float) (Math.sin(t) * ringR));
                pts[i] = new Vector3f(p).add(origin);
                Vector3f tangent = new Vector3f((float) -Math.sin(t), 0f, (float) Math.cos(t));
                Vector3f unit = p.lengthSquared() < 1.0E-8f ? new Vector3f(0, 1, 0) : new Vector3f(p).normalize();
                Vector3f r = tangent.cross(unit);
                rights[i] = r.lengthSquared() < 1.0E-8f ? null : r.normalize();
            }
            WorldFx.polyline(b, pose, pts, rights, halfWidth * 0.92f, 255, 198, 92,
                    (lat % 2 == 0) ? alpha : alphaDim);
        }
    }

    private static void drawNodes(BufferBuilder b, Matrix4f pose, Vector3f origin, float radius,
                                  float boost, float time) {
        int alpha = clamp(235 * boost);
        Vector3f[] dirs = {
                new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
                new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
                new Vector3f(0, 0, 1), new Vector3f(0, 0, -1),
        };

        for (int i = 0; i < dirs.length; i++) {
            Vector3f n = new Vector3f(dirs[i]).normalize();
            Vector3f base = new Vector3f(origin).add(new Vector3f(n).mul(radius));
            Vector3f tangent = Math.abs(n.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f t1 = new Vector3f(tangent).cross(n).normalize();
            Vector3f t2 = new Vector3f(n).cross(t1).normalize();

            float flicker = 0.7f + 0.3f * (float) Math.sin(time * 9.0 + i * 2.1);
            float len = radius * 0.09f * flicker;

            WorldFx.segment(b, pose, new Vector3f(base).sub(new Vector3f(t1).mul(len)),
                    new Vector3f(base).add(new Vector3f(t1).mul(len)), 0.026f, 255, 230, 150, alpha);
            WorldFx.segment(b, pose, new Vector3f(base).sub(new Vector3f(t2).mul(len)),
                    new Vector3f(base).add(new Vector3f(t2).mul(len)), 0.026f, 255, 230, 150, alpha);
            WorldFx.segment(b, pose, new Vector3f(base).add(new Vector3f(n).mul(-0.03f)),
                    new Vector3f(base).add(new Vector3f(n).mul(0.03f)), 0.050f, 255, 240, 190, alpha);
        }
    }

    private static void drawGyroRings(BufferBuilder b, Matrix4f pose, Vector3f origin, float radius,
                                      float boost, float time) {
        int alpha = clamp(130 * boost);
        float r = radius * 1.008f;

        for (int i = 0; i < 2; i++) {
            float spin = time * (0.35f + i * 0.22f);
            double tilt = i == 0 ? Math.toRadians(28) : Math.toRadians(-52);

            Vector3f axisA = new Vector3f((float) Math.cos(spin), 0f, (float) Math.sin(spin));
            Vector3f axisB = new Vector3f((float) -Math.sin(spin) * (float) Math.cos(tilt),
                    (float) Math.sin(tilt),
                    (float) Math.cos(spin) * (float) Math.cos(tilt));
            axisA.normalize();
            axisB.normalize();

            WorldFx.ring(b, pose, origin, axisA, axisB, r, 0.016f, 255, 190, 90, alpha, 64);
        }
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
