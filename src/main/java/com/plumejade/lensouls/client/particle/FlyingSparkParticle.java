package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 铁砧风格飞溅火花——挪用 block-place-particles 的 flying_spark 纹理。
 * 支持多种颜色（根据弹药类型）和 glow 渲染（无光照影响，夜晚高亮）。
 */
public class FlyingSparkParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected FlyingSparkParticle(ClientLevel level, double x, double y, double z,
                                  double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 10 + random.nextInt(15);
        this.quadSize = 0.03f + random.nextFloat() * 0.02f;
        this.gravity = 0.6f;
        this.friction = 0.95f;
        this.xd = xSpeed + (random.nextDouble() - 0.5) * 0.15;
        this.yd = ySpeed + random.nextDouble() * 0.1;
        this.zd = zSpeed + (random.nextDouble() - 0.5) * 0.15;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 生命周期后半段淡出
        if (age > lifetime * 0.6f) {
            this.alpha = 1.0f - (age - lifetime * 0.6f) / (lifetime * 0.4f);
        }
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_LIT;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // 强制最大亮度（OptiFine 式发光效果）
    }

    /** 橙色（地狱） */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            var p = new FlyingSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            p.setColor(1.0f, 0.6f + level.random.nextFloat() * 0.3f, 0.0f);
            return p;
        }
    }

    /** 亮绿色（主世界） */
    public static class GreenProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public GreenProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            var p = new FlyingSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            p.setColor(0.3f + level.random.nextFloat() * 0.2f, 1.0f, 0.3f + level.random.nextFloat() * 0.2f);
            return p;
        }
    }

    /** 紫色（末地） */
    public static class PurpleProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public PurpleProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            var p = new FlyingSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            p.setColor(0.7f + level.random.nextFloat() * 0.3f, 0.2f, 1.0f);
            return p;
        }
    }
}
