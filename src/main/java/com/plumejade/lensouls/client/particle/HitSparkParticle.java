package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 次元枪命中白色方形粒子——挪用 Aurae 的 ash 纹理。
 */
public class HitSparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected HitSparkParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 20 + random.nextInt(10);
        this.quadSize = 0.15f + random.nextFloat() * 0.1f;
        this.gravity = -0.02f; // 轻微上升（类似篝火灰烬）
        this.friction = 0.9f;
        this.xd = xSpeed + (random.nextDouble() - 0.5) * 0.2;
        this.yd = ySpeed + random.nextDouble() * 0.1;
        this.zd = zSpeed + (random.nextDouble() - 0.5) * 0.2;
        this.alpha = 0.9f;
        setAlpha(alpha);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 后半生命期逐渐淡出
        if (age > lifetime / 2) {
            setAlpha(1.0f - (float)(age - lifetime / 2) / (lifetime / 2));
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 白色灰烬粒子 */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new HitSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }

    /** 橙色火花粒子 */
    public static class OrangeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public OrangeProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            HitSparkParticle p = new HitSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            var r = level.random;
            p.setColor(1.0f, 0.5f + r.nextFloat() * 0.3f, 0.0f);
            return p;
        }
    }
}
