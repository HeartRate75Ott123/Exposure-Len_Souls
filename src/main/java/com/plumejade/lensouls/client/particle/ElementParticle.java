package com.plumejade.lensouls.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

/**
 * 元素灌注环境粒子（药水效果 {@code element_infusion} 身上那圈粒子）。
 * <p>
 * 贴图/配色是本模组自己的，<b>运动模型照抄原版药水效果粒子</b>
 * （{@code SpellParticle.MobEffectProvider} → {@code ParticleTypes.ENTITY_EFFECT}）：
 * <ul>
 *   <li>横向初速由粒子自己掷 {@code ±0.5} 再按摩擦衰减，纵向只用调用方给的 y 速度 ×0.2；</li>
 *   <li>{@code gravity = -0.1}（负重力＝微微上浮）、{@code friction = 0.96}、{@code hasPhysics = false}；</li>
 *   <li>寿命 {@code (int)(8 / (rand*0.8+0.2))} ＝ 8~40 刻，这是"原地冒一下就没"的关键。</li>
 * </ul>
 * <b>为什么不能直接用传进来的速度</b>：{@code LivingEntity.tickEffects} 里原版给效果粒子传的
 * 出生速度是哨兵值 {@code (1, 1, 1)}（原版 provider 根本不读），照抄成 {@code yd = 1.0} 就是
 * 每秒 20 格往上飞、还带 -0.005 的重力反推 ⇒ 实机看到"粒子以诡异速度飘向高空"。
 */
public class ElementParticle extends TextureSheetParticle {
    /** 与 {@code SpellParticle} 同款：每个粒子独立掷横向初速，共享一个 random 不影响观感 */
    private static final RandomSource RANDOM = RandomSource.create();

    private final SpriteSet sprites;

    protected ElementParticle(ClientLevel level, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed,
                              SpriteSet sprites, float r, float g, float b) {
        super(level, x, y, z, 0.5 - RANDOM.nextDouble(), ySpeed, 0.5 - RANDOM.nextDouble());
        this.sprites = sprites;
        this.setSpriteFromAge(sprites);

        this.friction = 0.96f;
        this.gravity = -0.1f;        // 原版药水粒子同款：负重力＝上浮
        this.speedUpWhenYMotionIsBlocked = true;
        this.hasPhysics = false;

        this.yd *= 0.2f;             // 纵向只用调用方给的 y 速度，且压到 1/5（原版口径）
        if (xSpeed == 0.0 && zSpeed == 0.0) {
            // 调用方明确不给横向初速（例如本模组自己补的粒子）时，横向再收 10 倍
            this.xd *= 0.1f;
            this.zd *= 0.1f;
        }

        this.lifetime = (int) (8.0 / (this.random.nextDouble() * 0.8 + 0.2));
        this.quadSize = 0.15f + this.random.nextFloat() * 0.1f;
        setColor(r, g, b);
        setAlpha(0.8f);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(sprites);
        // 后半段渐隐
        int fadeStart = lifetime / 2;
        if (this.age > fadeStart && lifetime > fadeStart) {
            this.alpha = 0.8f * (1.0f - (float) (this.age - fadeStart) / (lifetime - fadeStart));
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
