package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 夺魂索命的绿色魂火：从目标身上升起、螺旋、被抽向击杀者方向，末端淡出。
 * <p>
 * 贴图是自绘的柔和径向光点（{@code textures/particle/soul_wisp.png}）+ 半透明混合，
 * <b>不能用 alpha 测试的粒子类型</b>——那样大尺寸下会显示成硬边方块（既定踩坑）。
 */
public class SoulWispParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    private final float spiralPhase;
    private final float spiralSpeed;
    private final float riseSpeed;

    protected SoulWispParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 26 + random.nextInt(18);
        this.quadSize = 0.045f + random.nextFloat() * 0.035f;
        this.gravity = -0.008f;          // 轻微上浮
        this.friction = 0.972f;
        this.hasPhysics = false;
        this.spiralPhase = random.nextFloat() * 6.2832f;
        this.spiralSpeed = 1.6f + random.nextFloat() * 1.8f;
        this.riseSpeed = 0.014f + random.nextFloat() * 0.018f;
        this.xd = xSpeed * 0.35 + (random.nextDouble() - 0.5) * 0.02;
        this.yd = ySpeed * 0.25 + riseSpeed;
        this.zd = zSpeed * 0.35 + (random.nextDouble() - 0.5) * 0.02;

        float g = 0.90f + random.nextFloat() * 0.10f;
        this.setColor(0.22f + random.nextFloat() * 0.12f, g, 0.40f + random.nextFloat() * 0.22f);
        this.alpha = 0.9f;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);

        float age01 = (float) this.age / this.lifetime;
        float phase = spiralPhase + this.age * spiralSpeed * 0.35f;
        this.xd += Math.cos(phase) * 0.0018f;
        this.zd += Math.sin(phase) * 0.0018f;
        this.yd += riseSpeed * (1.0f - age01) * 0.5f;

        // 后 55% 淡出
        if (age01 > 0.45f) {
            this.alpha = 0.9f * (1.0f - (age01 - 0.45f) / 0.55f);
        }
        this.quadSize *= 1.006f;
    }

    /** 半透明混合：柔光晕，硬边方块感消失 */
    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    /** 紫色魂火（夺魂索命判定失败） */
    public static class FailProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public FailProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            SoulWispParticle particle = new SoulWispParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            // 紫罗兰：色调偏冷、饱和更低，作为"被挡下/无力"的观感
            particle.setColor(0.62f, 0.30f, 1.00f);
            return particle;
        }
    }

    /** 绿色魂火（夺魂索命成功） */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new SoulWispParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }

    /**
     * 银白魂尘（N公司2级员工「camera_shoot」闪光时从身上爆出）。
     * <p>
     * 与绿色魂火同一套运动（上浮 + 螺旋 + 末段淡出），只换配色：银白，色相微微偏冷。
     */
    public static class WhiteProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public WhiteProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            SoulWispParticle particle = new SoulWispParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            float w = 0.86f + level.random.nextFloat() * 0.14f;
            particle.setColor(w, Math.min(1.0f, w * 1.02f), 1.00f);
            return particle;
        }
    }
}
