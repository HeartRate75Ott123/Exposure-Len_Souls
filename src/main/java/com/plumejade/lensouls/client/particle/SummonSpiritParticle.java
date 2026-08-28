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
 * 折翼沉渊·祸之可能性召唤粒子（精灵）。
 * <p>
 * 召唤时于玩家身周方形落点生成，scale 1.2、生命 1.2s；第 1s 开始在粒子位置加入实体，
 * 同时粒子逐渐放大并渐隐（0.2s）。
 */
public class SummonSpiritParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private static final int LIFETIME = 30;       // 1.5s
    private static final int ENLARGE_AT = 20;     // 1s 后开始放大渐隐
    private static final float BASE_SIZE = 1.2f;

    protected SummonSpiritParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = LIFETIME;
        this.quadSize = BASE_SIZE;
        this.gravity = 0;
        this.friction = 1.0f;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        setAlpha(1.0f);
    }

    @Override
    public void tick() {
        this.age++;
        if (this.age >= this.lifetime) {
            this.remove();
            return;
        }
        this.setSpriteFromAge(sprites);
        if (this.age < ENLARGE_AT) {
            this.quadSize = BASE_SIZE;
            this.alpha = 1.0f;
        } else {
            float p = (this.age - ENLARGE_AT) / (float) (this.lifetime - ENLARGE_AT); // 0→1 over 0.5s
            this.quadSize = BASE_SIZE * (1.0f + 4.0f * p);   // 冲击波式扩散放大（末端约 5.8 倍）
            this.alpha = (1.0f - p) * (1.0f - p);            // 平滑渐隐
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

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SummonSpiritParticle(level, x, y, z, sprites);
        }
    }
}
