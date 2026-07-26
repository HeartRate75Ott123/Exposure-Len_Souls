package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 元素增伤爆发粒子 — 向外飞散，渐入渐隐。
 */
public class SpiralParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected SpiralParticle(ClientLevel level, double x, double y, double z,
                             double xSpeed, double ySpeed, double zSpeed,
                             SpriteSet sprites, float r, float g, float b) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 15 + random.nextInt(8);
        this.quadSize = 0.15f + random.nextFloat() * 0.05f;
        this.gravity = 0.01f;
        this.friction = 0.96f;
        setColor(r, g, b);
        setAlpha(0f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 前 3 tick 渐入
        if (this.age <= 3) {
            this.alpha = this.age / 3.0f;
        }
        // 后 5 tick 渐隐
        else if (this.age >= this.lifetime - 5) {
            this.alpha = (this.lifetime - this.age) / 5.0f;
        } else {
            this.alpha = 1.0f;
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }

    // ========== Provider ==========

    public static class FireProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public FireProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 1.0f, 0.45f, 0.1f);
        }
    }

    public static class WaterProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public WaterProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.2f, 0.6f, 1.0f);
        }
    }

    public static class EarthProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public EarthProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.55f, 0.35f, 0.15f);
        }
    }

    public static class EnderProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public EnderProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.7f, 0.2f, 0.65f);
        }
    }

    public static class ProjectileProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public ProjectileProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.047f, 1.0f, 0.514f);
        }
    }

    public static class WeaknessLensProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public WeaknessLensProvider(SpriteSet sprites) { this.sprites = sprites; }
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SpiralParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, 0.176f, 0.286f, 0.361f);
        }
    }
}
