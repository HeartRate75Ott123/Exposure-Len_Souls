package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 破韧粒子 — 十字架，向玩家方向高速散射。
 * <p>
 * 灰度贴图 + 代码着色，深红色 (0.8, 0.1, 0.0)。
 * 120° 锥角朝玩家方向，速度 0.8~1.2，无重力，满亮。
 */
public class ToughnessBreakParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected ToughnessBreakParticle(ClientLevel level, double x, double y, double z,
                                     double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);
        this.lifetime = 15 + random.nextInt(6);       // 15~20 tick
        this.quadSize = 0.25f;                         // 固定大小（25%）
        this.gravity = -0.1f;                          // 轻微上漂
        this.friction = 0.95f;                         // 等效拖拽 0.05
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        setColor(0.8f, 0.1f, 0.0f);                   // 深红色
        setAlpha(1.0f);
    }

    @Override
    public void tick() {
        double px = this.x, py = this.y, pz = this.z;
        super.tick();
        this.setSpriteFromAge(sprites);
        // 碰到方块 → 清除
        if (this.x == px && this.y == py && this.z == pz && this.age > 1) {
            this.remove();
            return;
        }
        // 后半段渐隐
        int fadeStart = lifetime * 2 / 3;
        if (this.age > fadeStart) {
            this.alpha = 1.0f - (float)(this.age - fadeStart) / (lifetime - fadeStart);
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

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ToughnessBreakParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
