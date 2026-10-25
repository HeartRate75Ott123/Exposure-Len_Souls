package com.plumejade.lensouls.client.fx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.plumejade.lensouls.particle.ModParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 世界空间瞬时特效：夺魂索命的「魂飞魄散」、时空回溯的落点。
 * <p>
 * 全部在 {@code AFTER_LEVEL} 由 {@link WorldFxPass} 驱动（Iris 兼容），
 * 动画靠着色器 uniform：
 * <ul>
 *   <li>{@code FxGrow} —— 魂丝从体内<b>沿路径生长</b>出来（不是一次性出现）；</li>
 *   <li>环<b>错峰连续推出</b>（ring 一个个冒出来并向外扩张）；</li>
 *   <li>{@code FxTaper} —— 消散时末端<b>散开、变细</b>，同时半径外扩、亮度衰减。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public final class WorldFxBursts {

    private interface Burst {
        boolean update(float dt);

        void render(Matrix4f pose, Vec3 camera, float time);
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    private WorldFxBursts() {
    }

    public static void clear() {
        BURSTS.clear();
    }

    public static boolean wantsRender() {
        return !BURSTS.isEmpty();
    }

    // =====================================================================
    //  入口
    // =====================================================================

    /**
     * 滤镜成功施加：被施加者身上「对焦收束」。
     * <p>
     * 只记实体 id + 颜色，位置每帧现取（见 {@link FilterAppliedBurst}）——被施加者走动、被击退、
     * 甚至被打飞时，特效都会平滑跟过去，而不是钉在发包那一瞬间的坐标。
     */
    public static void filterApplied(ClientLevel level, net.minecraft.world.entity.Entity target,
                                     float r, float g, float b) {
        BURSTS.add(new FilterAppliedBurst(target, r, g, b));

        // 胸口一圈效果色的魂尘：环是"框"，粒子是"尘"，两者一起把"覆上一层"的观感做实。
        // 半径与数量都按体型放大——巨兽身上 0.32 格的小圈等于看不见。
        float scale = bodyScaleOf(target.getBbWidth(), target.getBbHeight());
        double h = target.getBbHeight();
        double cx = target.getX();
        double cy = target.getY() + h * 0.55;
        double cz = target.getZ();
        int wisps = Math.min(30, Math.round(10 * scale));
        for (int i = 0; i < wisps; i++) {
            double ang = Math.PI * 2.0 * i / wisps;
            double rad = (0.32 + level.random.nextDouble() * 0.10) * scale;
            level.addParticle(ModParticleTypes.SOUL_WISP_WHITE.get(),
                    cx + Math.cos(ang) * rad,
                    cy + (level.random.nextDouble() - 0.5) * 0.18 * scale,
                    cz + Math.sin(ang) * rad,
                    0.0, 0.012 + level.random.nextDouble() * 0.02, 0.0);
        }
    }

    /** 体型缩放：包围盒对角 / 普通类人生物对角（与 {@link FilterAppliedBurst} 同一口径） */
    private static float bodyScaleOf(float width, float height) {
        float span = (float) Math.sqrt(width * width + height * height);
        return Math.max(0.55F, Math.min(3.40F, span / 1.90F));
    }

    /** 夺魂索命：目标身上「魂飞魄散」。 */
    public static void soulSever(ClientLevel level, Vec3 feet, float height, boolean success) {
        BURSTS.add(new SoulSeverBurst(feet, height, success));

        // 成功 = 绿色发射器；失败 = 紫色发射器（同形态、不同配色）
        var wispType = success ? ModParticleTypes.SOUL_WISP.get() : ModParticleTypes.SOUL_WISP_FAIL.get();
        int wisps = success ? 30 : 16;
        float h = Math.max(0.9F, height);
        for (int i = 0; i < wisps; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 0.9;
            double oz = (level.random.nextDouble() - 0.5) * 0.9;
            double oy = 0.1 + level.random.nextDouble() * h;
            level.addParticle(wispType,
                    feet.x + ox, feet.y + oy, feet.z + oz,
                    0.0, 0.02 + level.random.nextDouble() * 0.03, 0.0);
        }
        for (int i = 0; i < (success ? 12 : 6); i++) {
            double ang = Math.PI * 2 * i / (success ? 12.0 : 6.0);
            level.addParticle(wispType,
                    feet.x, feet.y + h * 0.58, feet.z,
                    Math.cos(ang) * 0.09, 0.05, Math.sin(ang) * 0.09);
        }
    }

    /** 时空回溯落点：脚下青色扩散环 + 上升时间尘埃。 */
    public static void recallArrive(ClientLevel level, Vec3 feet) {
        BURSTS.add(new RecallArriveBurst(feet));
        for (int i = 0; i < 18; i++) {
            double ang = Math.PI * 2 * i / 18.0;
            double r = 0.3 + level.random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.END_ROD,
                    feet.x + Math.cos(ang) * r, feet.y + 0.1 + level.random.nextDouble() * 0.6,
                    feet.z + Math.sin(ang) * r,
                    0.0, 0.04 + level.random.nextDouble() * 0.03, 0.0);
        }
    }

    // =====================================================================
    //  驱动
    // =====================================================================

    public static void render(Minecraft mc, Vec3 camera, Matrix4f pose, float time, float dt) {
        if (BURSTS.isEmpty()) return;
        Iterator<Burst> it = BURSTS.iterator();
        while (it.hasNext()) {
            Burst burst = it.next();
            if (!burst.update(dt)) it.remove();
            else burst.render(pose, camera, time);
        }
    }

    /** 夺魂索命：魂丝生长 → 环连续推出 → 散开变细消散。 */
    private static final class SoulSeverBurst implements Burst {
        private static final float DURATION = 1.35F;

        private final Vec3 feet;
        private final float height;
        private final boolean success;
        private float t;

        SoulSeverBurst(Vec3 feet, float height, boolean success) {
            this.feet = feet;
            this.height = Math.max(0.9F, height);
            this.success = success;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            return t < DURATION;
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float u = Math.min(1.0F, t / DURATION);
            Vector3f center = new Vector3f(
                    (float) (feet.x - camera.x),
                    (float) (feet.y - camera.y),
                    (float) (feet.z - camera.z));
            // 成功 = 绿色；失败 = 紫色
            int r = success ? 70 : 150;
            int g = success ? 255 : 90;
            int b = success ? 170 : 255;

            // ── 1. 魂丝：沿路径生长（前 22%），末端散开变细（后 45%） ──
            float grow = Math.min(1.0F, t / 0.30F);
            float taper = smoothstep(0.55F, 1.0F, u);
            float strandAlpha = (1.0F - u * u) * (success ? 1.0F : 0.5F);
            if (strandAlpha > 0.02F) {
                WorldFx.Params p = WorldFx.params();
                p.time = time;
                p.pulseSpeed = 0.9F;
                p.pulseSharp = 0.08F;
                p.edgeSoft = 1.35F;
                p.coreBoost = 0.95F;
                p.flicker = 0.4F;
                p.grow = grow;
                p.taper = taper;
                p.alpha = strandAlpha;

                BufferBuilder buffer = WorldFx.begin(p);
                int strands = success ? 9 : 4;
                for (int s = 0; s < strands; s++) {
                    double baseAng = Math.PI * 2.0 * s / strands + u * 1.3;
                    int segments = 10;
                    Vector3f[] pts = new Vector3f[segments];
                    for (int i = 0; i < segments; i++) {
                        float q = (float) i / (segments - 1);
                        // 消散时半径外扩（散开）
                        float spread = 1.0F + taper * 1.5F;
                        float radius = (0.12F + q * 0.30F) * spread;
                        float ang = (float) (baseAng + q * (2.9 + taper * 2.2));
                        float y = center.y + height * 0.55F + q * (height * (1.5F + taper * 0.5F));
                        pts[i] = new Vector3f(
                                center.x + (float) Math.cos(ang) * radius,
                                y,
                                center.z + (float) Math.sin(ang) * radius);
                    }
                    WorldFx.polyline(buffer, pose, pts, null, 0.036F, r, g, (int) (b * 0.9F),
                            (int) (255 * strandAlpha));
                }
                WorldFx.flush(buffer);
            }

            // ── 2. 环：错峰连续推出（每个环自己的一段生命周期） ──
            WorldFx.Params ringParams = WorldFx.params();
            ringParams.time = time;
            ringParams.pulseSpeed = 1.3F;
            ringParams.pulseSharp = 0.06F;
            ringParams.edgeSoft = 1.3F;
            ringParams.coreBoost = 1.0F;
            // 环是大面积细带，FxFlicker 是"沿圆周的高频噪声"，0.3 在浅视角下会被读成频闪；
            // 环要稳定发光，跳动只留给沿圆周跑的脉冲。
            ringParams.flicker = 0.10F;
            ringParams.alpha = 1.0F;

            BufferBuilder rings = WorldFx.begin(ringParams);
            Vector3f axisX = new Vector3f(1f, 0f, 0f);
            Vector3f axisZ = new Vector3f(0f, 0f, 1f);
            int ringCount = success ? 7 : 3;
            for (int i = 0; i < ringCount; i++) {
                float start = i * 0.075F;
                float local = (u - start) / 0.55F;
                if (local <= 0.0F || local >= 1.0F) continue;
                float radius = 0.55F + local * (1.4F + i * 0.22F);
                float y = center.y + 0.10F + local * (height * 0.9F) + i * 0.05F;
                int a = (int) ((1.0F - local) * (1.0F - local) * 230 * (success ? 1.0F : 0.5F));
                if (a <= 3) continue;
                WorldFx.ring(rings, pose, new Vector3f(center.x, y, center.z), axisX, axisZ,
                        radius, 0.032f, r, g, b, a, 56);
            }
            WorldFx.flush(rings);

            // ── 3. 魂柱：从胸口向上长出来，随后变细消散 ──
            if (success && grow > 0.05F) {
                WorldFx.Params p = WorldFx.params();
                p.time = time;
                p.pulseSpeed = 0.55F;
                p.pulseSharp = 0.10F;
                p.edgeSoft = 2.0F;
                p.coreBoost = 0.5F;
                p.flicker = 0.25F;
                p.grow = grow;
                p.taper = taper;
                p.alpha = (1.0F - u * u) * 0.55F;

                BufferBuilder pillar = WorldFx.begin(p);
                float top = center.y + height * (0.9F + taper * 0.9F);
                float wide = 0.20F * (1.0F - taper * 0.7F);
                WorldFx.polyline(pillar, pose, new Vector3f[]{
                        new Vector3f(center.x, center.y + height * 0.42F, center.z),
                        new Vector3f(center.x, top, center.z),
                }, null, wide, r, g, (int) (b * 0.85F), (int) (255 * p.alpha));
                WorldFx.flush(pillar);
            }
        }
    }

    /**
     * 滤镜施加的「对焦收束」：三个元素合成一次"被套上一层滤镜"的观感。
     * <ol>
     *   <li><b>咬合环</b>（银白偏效果色）：半径 {@code 1.30 → 0.42} × 目标体型，0.30s 内 ease-out 收拢；</li>
     *   <li><b>上色环</b>（效果色）：半径 {@code 0.36 → 1.62} × 目标体型，0.60s 内向外荡开并淡出；</li>
     *   <li><b>刻度线</b>（效果色）：咬合环上绕 8 根短线，随咬合一起收、一起淡（取景器/镜头的刻度语汇）。</li>
     * </ol>
     * 环都取<b>相机朝向</b>的平面（right / up 由"目标→镜头"方向现算），因此从任何角度看都是正圆，
     * 永远像"套在这个目标身上的一圈"，不会因为侧看塌成一条线。
     *
     * <h3>体型缩放（为什么不能写死 1.3 格）</h3>
     * 半径原先按<b>绝对格数</b>写死，套在僵尸身上刚好，套在下界合金巨兽那种 4×6 的体型上
     * 就只是胸口一小圈，"上了滤镜"完全看不出来。现在用目标的包围盒对角
     * {@code sqrt(w² + h²)} 对普通类人生物（0.6×1.8 ≈ 1.90）取比值，作为整体缩放：
     * <ul>
     *   <li>普通生物 ≈ 1.0（观感与之前一致）；小型生物夹到 0.55；</li>
     *   <li>巨兽 ≈ 2.5~3.4（夹上限），环线宽度、刻度长度、粒子散热半径一起放大；</li>
     *   <li>体型缩放 ≥ 1.5 时，在 {@code 0.30h / 0.55h / 0.80h} 三层各来一圈
     *       —— 高个子只圈中间会像"腰带"，三层才是"整只被套住"。</li>
     * </ul>
     *
     * <h3>平滑移动</h3>
     * 不存死坐标，每帧向实体胸口做指数趋近（帧率无关的 {@code 1 - e^{-16·dt}}，约 60ms 追上）；
     * 位置、身高、体型三者都做同样的平滑（阶段变化突然变大的 boss 不会让环"啪"地跳一下）。
     * 实体卸载或死亡后保持最后位置把动画播完，既不瞬移也不凭空消失。
     * 目标是自己时不能用胸口（相机就长在胸口，环会糊满屏幕）——改成镜头正前方 1.15 格、半径减半。
     */
    /**
     * "贴在某个实体身上"的特效基类：负责三件所有这类特效都要的事——
     * <ol>
     *   <li><b>体型缩放</b>：包围盒对角 {@code sqrt(w² + h²)} 对普通类人生物（0.6×1.8 ≈ 1.90）取比值，
     *       普通生物 ≈ 1.0、小型夹 0.55、巨兽夹到 3.4；环半径/线宽/刻度长度都乘它；</li>
     *   <li><b>平滑跟随</b>：只存 entityId，位置/身高/体型每帧向实体做指数趋近
     *       （帧率无关 {@code 1 - e^{-16·dt}}，约 60ms 追上）；实体卸载或死亡后保持最后位置播完；</li>
     *   <li><b>绘制坐标</b>：给出相机朝向平面上的一对轴（{@link #facingAxes}），
     *       这样环从任何角度看都是正圆，不会侧看塌成一条线；自身目标改用镜头正前方。</li>
     * </ol>
     */
    private abstract static class BodyFollowBurst implements Burst {
        /** 普通类人生物的包围盒对角，作为体型缩放的分母 */
        protected static final float HUMANOID_SPAN = 1.90F;
        /** 跟随时长常数：越大跟得越紧（16 ≈ 60ms 追上） */
        protected static final float FOLLOW_K = 16.0F;
        protected static final float BASE_DURATION = 0.70F;

        protected final int entityId;
        protected final boolean self;

        /** 平滑跟踪得到的胸口位置（世界坐标） */
        protected Vec3 pos;
        protected float height;
        /** 平滑跟踪得到的包围盒对角（体型缩放用） */
        protected float span;
        protected float t;

        protected BodyFollowBurst(net.minecraft.world.entity.Entity target) {
            this.entityId = target.getId();
            Minecraft mc = Minecraft.getInstance();
            this.self = mc.player != null && mc.player.getId() == entityId;
            double h = target.getBbHeight();
            this.height = (float) h;
            this.span = (float) Math.sqrt(target.getBbWidth() * target.getBbWidth() + h * h);
            if (!self) {
                this.pos = new Vec3(target.getX(), target.getY() + h * 0.55, target.getZ());
            }
        }

        /** 体型缩放：普通生物 ≈ 1.0，巨兽夹到 3.4（再大就不是"一圈"而是天花板了） */
        protected float bodyScale() {
            return Math.max(0.55F, Math.min(3.40F, span / HUMANOID_SPAN));
        }

        protected float duration() {
            return BASE_DURATION;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            float life = duration();
            if (self) return t < life;

            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || pos == null) return t < life;

            net.minecraft.world.entity.Entity e = level.getEntity(entityId);
            // 实体没了（死亡/卸载/超出追踪距离）：保持最后位置，让这一下正常播完
            if (e == null || !e.isAlive()) return t < life;

            double h = e.getBbHeight();
            double wantX = e.getX();
            double wantY = e.getY() + h * 0.55;
            double wantZ = e.getZ();
            float wantSpan = (float) Math.sqrt(e.getBbWidth() * e.getBbWidth() + h * h);
            if (dt <= 0.0F) {
                pos = new Vec3(wantX, wantY, wantZ);
                height = (float) h;
                span = wantSpan;
            } else {
                // 位置 / 身高 / 体型用同一条平滑曲线：boss 换阶段突然变高变大时，环不会"啪"地跳
                float a = 1.0F - (float) Math.exp(-FOLLOW_K * dt);
                pos = new Vec3(
                        pos.x + (wantX - pos.x) * a,
                        pos.y + (wantY - pos.y) * a,
                        pos.z + (wantZ - pos.z) * a);
                height += ((float) h - height) * a;
                span += (wantSpan - span) * a;
            }
            return t < life;
        }

        /** 相机空间里的绘制中心；目标是自己时放到镜头正前方 1.15 格（否则环会糊满屏幕） */
        protected Vector3f centerOf(Vec3 camera) {
            if (self) {
                Minecraft mc = Minecraft.getInstance();
                Vec3 look = mc.player != null ? mc.player.getLookAngle() : new Vec3(0.0, 0.0, 1.0);
                return new Vector3f((float) (look.x * 1.15), (float) (look.y * 1.15 - 0.18),
                        (float) (look.z * 1.15));
            }
            if (pos == null) return null;
            return new Vector3f((float) (pos.x - camera.x),
                    (float) (pos.y - camera.y),
                    (float) (pos.z - camera.z));
        }

        /** 相机朝向平面的一对轴 {@code {right, up}}；中心就在镜头上时返回 null */
        protected static Vector3f[] facingAxes(Vector3f center) {
            Vector3f view = new Vector3f(-center.x, -center.y, -center.z);
            if (view.lengthSquared() < 1.0E-6F) return null;
            view.normalize();
            Vector3f right = new Vector3f(0F, 1F, 0F).cross(view);
            if (right.lengthSquared() < 1.0E-5F) right.set(1F, 0F, 0F);
            right.normalize();
            Vector3f up = new Vector3f(view).cross(right).normalize();
            return new Vector3f[]{right, up};
        }
    }

    private static final class FilterAppliedBurst extends BodyFollowBurst {
        private final float cr;
        private final float cg;
        private final float cb;

        FilterAppliedBurst(net.minecraft.world.entity.Entity target, float r, float g, float b) {
            super(target);
            this.cr = r;
            this.cg = g;
            this.cb = b;
        }

        /** 体型越大，动画给得越长（0.70 ~ 0.94s），否则巨兽上的环还没读完就没了 */
        @Override
        protected float duration() {
            return BASE_DURATION + 0.12F * Math.min(2.0F, Math.max(0.0F, bodyScale() - 1.0F));
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float life = duration();
            float u = Math.min(1.0F, t / life);
            if (u >= 1.0F) return;

            float scale = bodyScale();
            Vector3f center = centerOf(camera);
            if (center == null) return;
            Vector3f[] axes = facingAxes(center);
            if (axes == null) return;
            Vector3f right = axes[0];
            Vector3f up = axes[1];
            float radiusScale = self ? 0.5F : scale;

            // ① 咬合：1.30 → 0.42（× 体型），ease-out
            float squeeze = Math.min(1.0F, t / (0.30F * Math.max(1.0F, scale * 0.72F)));
            float squeezeEase = 1.0F - (1.0F - squeeze) * (1.0F - squeeze);
            float squeezeR = (1.30F - 0.88F * squeezeEase) * radiusScale;
            int squeezeA = Math.round(235.0F * smoothstep(0.0F, 0.05F, t)
                    * (1.0F - smoothstep(0.24F, 0.62F, t)));

            // ② 上色：0.36 → 1.62，向外荡开
            float spread = Math.min(1.0F, t / 0.60F);
            float spreadEase = 1.0F - (1.0F - spread) * (1.0F - spread);
            float spreadR = (0.36F + 1.26F * spreadEase) * radiusScale;
            int spreadA = Math.round(220.0F * smoothstep(0.0F, 0.04F, t)
                    * (1.0F - spread) * (1.0F - spread));

            // 线宽与刻度长度也跟体型走：4 格宽的环配 0.02 的细线会细到看不见。
            // 基准值比第一版加粗约 60%（用户实机反馈"线条可以适当粗一些"）
            float lineK = 0.75F + 0.45F * scale;
            float squeezeW = 0.032F * lineK;
            float spreadW = 0.048F * lineK;
            float tickIn = 0.045F * lineK;
            float tickLen = 0.110F * lineK;

            WorldFx.Params p = WorldFx.params();
            p.time = time;
            p.pulseSpeed = 1.5F;
            p.pulseSharp = 0.12F;   // 比魂丝柔和：这是镜头刻度，不该像电
            p.edgeSoft = 1.5F;
            p.coreBoost = 0.9F;
            p.flicker = 0.12F;
            p.grow = 1.0F;
            p.taper = 0.0F;
            p.alpha = 1.0F;

            BufferBuilder buffer = WorldFx.begin(p);

            // 只有<b>一圈</b>咬合环。曾经为大体型做过"0.30h / 0.55h / 0.80h 三层"，
            // 实机看是"一个实体身上出现了两层法印、一上一下"（焰魔那种体型特别明显）——
            // 加量不等于加强，观感反而像叠了两个特效。大体型继续只靠 radiusScale 放大。
            if (squeezeA > 3) {
                WorldFx.ring(buffer, pose, center, right, up, squeezeR, squeezeW,
                        tint(150, 0.42F, cr), tint(160, 0.42F, cg), tint(178, 0.42F, cb),
                        squeezeA, 64);
            }
            if (spreadA > 3) {
                WorldFx.ring(buffer, pose, center, right, up, spreadR, spreadW,
                        tint(55, 0.85F, cr), tint(58, 0.85F, cg), tint(62, 0.85F, cb),
                        spreadA, 64);
            }

            int tickA = Math.round(squeezeA * 0.75F);
            if (tickA > 3) {
                int ticks = scale >= 2.0F ? 12 : 8;
                for (int i = 0; i < ticks; i++) {
                    double ang = Math.PI * 2.0 * i / ticks + Math.PI / ticks;
                    float dirX = (float) Math.cos(ang);
                    float dirY = (float) Math.sin(ang);
                    Vector3f inner = new Vector3f(center)
                            .add(new Vector3f(right).mul(dirX * (squeezeR + tickIn)))
                            .add(new Vector3f(up).mul(dirY * (squeezeR + tickIn)));
                    Vector3f outer = new Vector3f(center)
                            .add(new Vector3f(right).mul(dirX * (squeezeR + tickIn + tickLen)))
                            .add(new Vector3f(up).mul(dirY * (squeezeR + tickIn + tickLen)));
                    WorldFx.polyline(buffer, pose, new Vector3f[]{inner, outer}, null,
                            0.022F * lineK,
                            tint(90, 0.75F, cr), tint(92, 0.75F, cg), tint(96, 0.75F, cb), tickA);
                }
            }

            WorldFx.flush(buffer);
        }

        /** 底色 + 效果色（gain 越大越"上色"）；暗色效果也留得住可见度 */
        private static int tint(int base, float gain, float c) {
            return Math.max(0, Math.min(255, Math.round(base + gain * 255.0F * c)));
        }
    }

    /**
     * 要害打击命中：受击者身上「血线贯穿」。
     * <p>
     * 与滤镜的「对焦收束」共用 {@link BodyFollowBurst}（体型缩放 + 平滑跟随），
     * 但形态与配色完全不同：白色冲击属于「削韧」的通用语汇，要害打击用<b>红</b>，
     * 一条收束环 + 四道从胸口撕出去的血线 + 白热芯十字，短促（0.5s）而尖锐。
     * <p>
     * <b>与既有削韧反馈共存</b>：抖屏走 {@code CameraShake}（相机角度），定身冲击走屏幕后处理通道，
     * 这里只画世界空间缎带 + 粒子，三套互不占用的资源，所以同时触发不会互相顶掉。
     */
    public static void vitalStrike(ClientLevel level, net.minecraft.world.entity.Entity target) {
        BURSTS.add(new VitalStrikeBurst(target));

        // 红色魂尘：数量与散布半径都跟体型走（巨兽身上 0.3 格的小圈等于看不见）
        float scale = bodyScaleOf(target.getBbWidth(), target.getBbHeight());
        double h = target.getBbHeight();
        double cx = target.getX();
        double cy = target.getY() + h * 0.55;
        double cz = target.getZ();
        int wisps = Math.min(26, Math.round(9 * scale));
        for (int i = 0; i < wisps; i++) {
            double ang = Math.PI * 2.0 * i / wisps;
            double rad = (0.28 + level.random.nextDouble() * 0.14) * scale;
            level.addParticle(ModParticleTypes.SOUL_WISP_RED.get(),
                    cx + Math.cos(ang) * rad,
                    cy + (level.random.nextDouble() - 0.5) * 0.22 * scale,
                    cz + Math.sin(ang) * rad,
                    0.0, 0.02 + level.random.nextDouble() * 0.03, 0.0);
        }
    }

    /** 要害打击：红色收束环 + 四道血线 + 白热芯十字。 */
    private static final class VitalStrikeBurst extends BodyFollowBurst {
        private static final float LIFE = 0.52F;

        VitalStrikeBurst(net.minecraft.world.entity.Entity target) {
            super(target);
        }

        @Override
        protected float duration() {
            return LIFE + 0.06F * Math.min(2.0F, Math.max(0.0F, bodyScale() - 1.0F));
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float life = duration();
            if (t >= life) return;
            float scale = bodyScale();
            Vector3f center = centerOf(camera);
            if (center == null) return;
            Vector3f[] axes = facingAxes(center);
            if (axes == null) return;
            Vector3f right = axes[0];
            Vector3f up = axes[1];
            float radiusScale = self ? 0.5F : scale;
            // 线宽比滤镜那套更粗一档：这一下短促，必须"一眼看见"
            float lineK = 0.85F + 0.55F * scale;

            WorldFx.Params p = WorldFx.params();
            p.time = time;
            p.pulseSpeed = 2.4F;
            p.pulseSharp = 0.10F;
            p.edgeSoft = 1.3F;
            p.coreBoost = 1.0F;
            p.flicker = 0.15F;
            p.grow = 1.0F;
            p.taper = 0.0F;
            p.alpha = 1.0F;

            BufferBuilder buffer = WorldFx.begin(p);

            // ① 红环收束：1.15 → 0.45（× 体型），0.26s 内咬死
            float sq = Math.min(1.0F, t / (0.26F * Math.max(1.0F, scale * 0.6F)));
            float sqE = 1.0F - (1.0F - sq) * (1.0F - sq);
            float ringR = (1.15F - 0.70F * sqE) * radiusScale;
            int ringA = Math.round(250.0F * smoothstep(0.0F, 0.04F, t)
                    * (1.0F - smoothstep(0.20F, 0.50F, t)));
            if (ringA > 3) {
                WorldFx.ring(buffer, pose, center, right, up, ringR, 0.052F * lineK,
                        255, 58, 48, ringA, 64);
            }

            // ② 四道血线：0.22s 内撕满，末端渐隐
            float lineU = Math.min(1.0F, t / 0.22F);
            float lineE = 1.0F - (1.0F - lineU) * (1.0F - lineU);
            int lineA = Math.round(255.0F * (1.0F - smoothstep(0.22F, 0.52F, t)));
            if (lineA > 3) {
                for (int i = 0; i < 4; i++) {
                    double ang = Math.PI * 0.25 + i * Math.PI * 0.5 + t * 0.6;
                    float dx = (float) Math.cos(ang);
                    float dy = (float) Math.sin(ang);
                    float len = (0.95F + 0.28F * (i & 1)) * radiusScale * lineE;
                    float bend = 0.10F * radiusScale * ((i & 1) == 0 ? 1.0F : -1.0F);
                    Vector3f dir = new Vector3f(right).mul(dx).add(new Vector3f(up).mul(dy));
                    Vector3f perp = new Vector3f(right).mul(-dy).add(new Vector3f(up).mul(dx));
                    Vector3f[] pts = new Vector3f[]{
                            new Vector3f(center).add(new Vector3f(dir).mul(len * 0.30F)),
                            new Vector3f(center).add(new Vector3f(dir).mul(len * 0.68F))
                                    .add(new Vector3f(perp).mul(bend)),
                            new Vector3f(center).add(new Vector3f(dir).mul(len)),
                    };
                    WorldFx.polyline(buffer, pose, pts, null, 0.040F * lineK,
                            255, 64, 52, lineA, new float[]{1.0F, 0.7F, 0.0F});
                }
            }

            // ③ 白热芯十字：命中那一瞬间的"要害"闪光，最短最亮
            int coreA = Math.round(255.0F * (1.0F - smoothstep(0.06F, 0.26F, t)));
            if (coreA > 3) {
                float arm = 0.34F * radiusScale;
                WorldFx.polyline(buffer, pose, new Vector3f[]{
                        new Vector3f(center).add(new Vector3f(right).mul(-arm)),
                        new Vector3f(center).add(new Vector3f(right).mul(arm)),
                }, null, 0.034F * lineK, 255, 236, 228, coreA);
                WorldFx.polyline(buffer, pose, new Vector3f[]{
                        new Vector3f(center).add(new Vector3f(up).mul(-arm)),
                        new Vector3f(center).add(new Vector3f(up).mul(arm)),
                }, null, 0.034F * lineK, 255, 236, 228, coreA);
                WorldFx.ring(buffer, pose, center, right, up, 0.16F * radiusScale, 0.026F * lineK,
                        255, 240, 232, coreA, 40);
            }

            WorldFx.flush(buffer);
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0.0F, Math.min(1.0F, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0F - 2.0F * t);
    }

    /** 时空回溯落点：青色扩散环 + 短光柱。 */
    private static final class RecallArriveBurst implements Burst {
        private static final float DURATION = 0.75F;

        private final Vec3 feet;
        private float t;

        RecallArriveBurst(Vec3 feet) {
            this.feet = feet;
        }

        @Override
        public boolean update(float dt) {
            t += dt;
            return t < DURATION;
        }

        @Override
        public void render(Matrix4f pose, Vec3 camera, float time) {
            float u = Math.min(1.0F, t / DURATION);
            float fade = 1.0F - u * u;
            if (fade <= 0.02F) return;

            Vector3f center = new Vector3f(
                    (float) (feet.x - camera.x),
                    (float) (feet.y - camera.y),
                    (float) (feet.z - camera.z));

            WorldFx.Params p = WorldFx.params();
            p.time = time;
            p.pulseSpeed = 2.2F;
            p.pulseSharp = 0.05F;
            p.edgeSoft = 1.4F;
            p.coreBoost = 1.0F;
            p.flicker = 0.2F;
            p.grow = 1.0F;
            p.taper = smoothstep(0.5F, 1.0F, u);
            p.alpha = fade;

            BufferBuilder buffer = WorldFx.begin(p);
            Vector3f axisX = new Vector3f(1f, 0f, 0f);
            Vector3f axisZ = new Vector3f(0f, 0f, 1f);

            for (int i = 0; i < 3; i++) {
                float local = (u - i * 0.08F) / 0.6F;
                if (local <= 0.0F || local >= 1.0F) continue;
                float radius = 0.28F + local * 1.6F;
                int a = (int) ((1.0F - local) * (1.0F - local) * 220);
                if (a <= 3) continue;
                WorldFx.ring(buffer, pose, new Vector3f(center.x, center.y + 0.06F + i * 0.05F, center.z),
                        axisX, axisZ, radius, 0.026f, 120, 220, 255, a, 56);
            }

            for (int s = 0; s < 4; s++) {
                double ang = Math.PI * 2.0 * s / 4.0 + 0.4;
                Vector3f[] pts = new Vector3f[6];
                for (int i = 0; i < 6; i++) {
                    float q = (float) i / 5.0F;
                    pts[i] = new Vector3f(
                            center.x + (float) Math.cos(ang) * 0.12F * (1.0F - q),
                            center.y + 0.05F + q * (1.5F * fade + 0.3F),
                            center.z + (float) Math.sin(ang) * 0.12F * (1.0F - q));
                }
                WorldFx.polyline(buffer, pose, pts, null, 0.024F, 110, 210, 255, (int) (255 * fade));
            }

            WorldFx.flush(buffer);
        }
    }
}
