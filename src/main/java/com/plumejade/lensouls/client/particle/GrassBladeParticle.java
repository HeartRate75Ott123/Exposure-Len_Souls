package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 草飘动粒子——从 block-place-particles 模组搬运。
 * 用于次元枪主世界子弹的飞行尾迹，表现飘落的草叶。
 */
public class GrassBladeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private float rotSpeed;
    private final float spinAcceleration;

    protected GrassBladeParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.setSprite(sprites.get(random));
        this.lifetime = 20 + random.nextInt(15);
        this.quadSize = 0.06f + random.nextFloat() * 0.04f;
        this.setSize(quadSize, quadSize);
        this.gravity = 0.3f;
        this.friction = 1.0f;
        this.xd = xSpeed + (random.nextDouble() - 0.5) * 0.08;
        this.yd = ySpeed + (random.nextDouble() - 0.5) * 0.08;
        this.zd = zSpeed + (random.nextDouble() - 0.5) * 0.08;
        this.spinAcceleration = (float) Math.toRadians(random.nextBoolean() ? -5.0 : 5.0);
        this.roll = (float) Math.toRadians(random.nextInt(360));
        this.oRoll = this.roll;
        // 绿色着色
        setColor(0.2f + random.nextFloat() * 0.3f, 0.6f + random.nextFloat() * 0.4f, 0.1f + random.nextFloat() * 0.2f);
    }

    @Override
    public void tick() {
        this.rotSpeed += this.spinAcceleration / 2.0f;
        this.oRoll = this.roll;
        if (!this.onGround) {
            this.roll += this.rotSpeed / 5.0f;
        }
        this.xd *= 0.95f;
        this.yd *= 0.9f;
        this.zd *= 0.95f;
        super.tick();
        // 淡出
        if (age > lifetime * 0.6f) {
            this.alpha = 1.0f - (age - lifetime * 0.6f) / (lifetime * 0.4f);
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new GrassBladeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
