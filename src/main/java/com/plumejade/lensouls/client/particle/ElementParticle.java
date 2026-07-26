package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 元素灌注环境粒子 — 环绕玩家缓慢漂浮，半透明渐隐。
 * <p>
 * 灰度贴图 + 代码着色，每种元素独立粒子类型 + 配色。
 */
public class ElementParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected ElementParticle(ClientLevel level, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed,
                              SpriteSet sprites, float r, float g, float b) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 30 + random.nextInt(20);
        this.quadSize = 0.15f + random.nextFloat() * 0.1f;
        this.gravity = -0.005f;      // 微微上浮
        this.friction = 0.98f;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        setColor(r, g, b);
        setAlpha(0.8f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 后半段渐隐
        int fadeStart = lifetime / 2;
        if (this.age > fadeStart) {
            this.alpha = 0.8f * (1.0f - (float)(this.age - fadeStart) / (lifetime - fadeStart));
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 满亮 */
    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    // ========== 四元素 Provider ==========

    /** 火焰：红橙渐变 (1.0, 0.45, 0.1) */
    public static class FireProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public FireProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ElementParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 1.0f, 0.45f, 0.1f);
        }
    }

    /** 水流：青蓝 (0.2, 0.6, 1.0) */
    public static class WaterProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public WaterProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ElementParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.2f, 0.6f, 1.0f);
        }
    }

    /** 大地：土褐 (0.55, 0.35, 0.15) */
    public static class EarthProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public EarthProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ElementParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.55f, 0.35f, 0.15f);
        }
    }

    /** 末影：紫红 (0.7, 0.2, 0.65) */
    public static class EnderProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public EnderProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ElementParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.7f, 0.2f, 0.65f);
        }
    }
}
