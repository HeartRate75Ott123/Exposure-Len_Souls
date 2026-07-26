package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 破韧冲击波粒子 — 圆环，在实体中心原地缩放 + 淡出。
 * <p>
 * 灰度贴图 + 代码着色，青蓝色 (0.0, 0.8, 1.0)。
 * 固定位置不动，quadSize 0.1→3.0，alpha 1→0，寿命 20 tick。
 */
public class ToughnessShockwaveParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float startSize;
    private final float endSize;

    protected ToughnessShockwaveParticle(ClientLevel level, double x, double y, double z,
                                         SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 20;
        this.startSize = 0.8f;
        this.endSize = 4.0f;
        this.quadSize = startSize;
        this.gravity = 0;
        this.friction = 1.0f;                          // 不减速
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        setColor(0.0f, 1.0f, 0.98f);                   // 青亮色 #00FFFA
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
        float progress = (float) this.age / this.lifetime;   // 0→1
        this.quadSize = startSize + (endSize - startSize) * progress;
        this.alpha = 1.0f - progress;
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

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ToughnessShockwaveParticle(level, x, y, z, sprites);
        }
    }
}
