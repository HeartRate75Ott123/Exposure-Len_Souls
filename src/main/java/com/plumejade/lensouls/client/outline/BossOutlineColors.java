package com.plumejade.lensouls.client.outline;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * BOSS 镜魂描边配色方案（支持多色渐变）。
 * <p>
 * 每个 BOSS 对应一组主色 + 渐变色列表，用于手持物品的发光描边效果。
 * 着色器按时间在这些颜色间循环流动。
 *
 * @param color1 主色 R/G/B
 * @param color2 次色 R/G/B
 * @param color3 第三色 R/G/B（可选，亮度为 0 时禁用）
 * @param color4 第四色 R/G/B（可选，亮度为 0 时禁用）
 * @param glowStrength 发光强度
 * @param outlineWidth 描边宽度
 */
public record BossOutlineColors(
        float[] color1, float[] color2,
        float[] color3, float[] color4,
        float glowStrength, float outlineWidth) {

    // ============ BOSS 配色方案 ============

    /** 焰魔 — 烈焰 */
    public static final BossOutlineColors IGNIS = new BossOutlineColors(
            rgb(1.8f, 0.4f, 0.0f),      // 炽橙
            rgb(1.5f, 1.2f, 0.0f),      // 金黄
            rgb(1.4f, 0.2f, 0.0f),      // 焰红
            rgb(0.8f, 0.0f, 0.0f),      // 暗红
            1.2f, 3.0f
    );

    /** 云筑魔像 — 冰霜 */
    public static final BossOutlineColors CLOUD_GOLEM = new BossOutlineColors(
            hex(0x84eeff),
            hex(0x1dabff),
            hex(0x1b4e98),
            hex(0xd2d8e1),
            1.0f, 2.8f
    );

    /** 堕落圣骑 — 堕落 */
    public static final BossOutlineColors POSSESSED_PALADIN = new BossOutlineColors(
            hex(0x626b6e),
            hex(0x626b6e),
            hex(0xdbe5e9),
            hex(0x3fe3ed),
            0.9f, 2.8f
    );

    /** 湮灭构造体 — 毒蚀 */
    public static final BossOutlineColors OBLITERATOR = new BossOutlineColors(
            hex(0x4cff1a),
            hex(0xa9ff00),
            hex(0x264840),
            hex(0xe7edaf),
            1.2f, 3.2f
    );

    /** 末影守卫 — 末影 */
    public static final BossOutlineColors ENDER_GUARDIAN = new BossOutlineColors(
            hex(0x3c3a3c),
            hex(0xffe500),
            hex(0x5a1a68),
            hex(0x271a38),
            1.0f, 2.8f
    );

    /** 下界合金巨兽 — 熔狱 */
    public static final BossOutlineColors NETHERITE_MONSTROSITY = new BossOutlineColors(
            hex(0xfaf695),
            hex(0xfbdc61),
            hex(0xcf200b),
            hex(0x3c3a3c),
            1.2f, 3.0f
    );

    // ========== 工厂方法 ==========

    private static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }

    /** 从 0xRRGGBB 整数解析为 RGB 浮点数组（自动 /255 归一化） */
    private static float[] hex(int hex) {
        float r = ((hex >> 16) & 0xFF) / 255f;
        float g = ((hex >> 8) & 0xFF) / 255f;
        float b = (hex & 0xFF) / 255f;
        return new float[]{r, g, b};
    }

    /** 零色（禁用多色） */
    private static float[] zrgb() {
        return new float[]{0.0f, 0.0f, 0.0f};
    }

    /** 6 个 BOSS 类型的配色数量（用于 {@code SoulGlowRenderTypes} 缓存） */
    public static final int BOSS_TYPE_COUNT = 6;

    /**
     * 从玩家的活跃元素附魔效果检测当前激活的 BOSS 类型并返回配色。
     * <p>
     * 多个 BOSS 效果可同时存在时，返回剩余时长最长的（最新激活的）。
     * 效果到期后自动返回 null → 描边消失。
     */
    public static BossOutlineColors fromPlayer(Player player) {
        return fromEntity(player);
    }

    /**
     * 从任意活跃实体的元素附魔效果检测 BOSS 类型并返回配色。
     * <p>
     * 适用于 {@link SoulGlowLayer} 对任意 {@link LivingEntity} 的描边检测。
     */
    public static BossOutlineColors fromEntity(LivingEntity entity) {
        BossPhantomType bestType = null;
        int bestDuration = -1;

        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof ElementInfusionEffect effect) {
                int amp = inst.getAmplifier();
                float mult = effect.getDamageMultiplier(amp);
                ElementDamage element = effect.getElement();

                for (BossPhantomType type : BossPhantomType.values()) {
                    if (type.getElement() == element
                            && Math.abs(type.getDamageMultiplier() - mult) < 0.01f) {
                        int dur = inst.getDuration();
                        if (dur > bestDuration) {
                            bestDuration = dur;
                            bestType = type;
                        }
                    }
                }
            }
        }

        if (bestType != null) {
            return fromBossType(bestType);
        }
        return null;
    }

    public static BossOutlineColors fromBossType(BossPhantomType type) {
        return switch (type) {
            case IGNIS                -> IGNIS;
            case CLOUD_GOLEM          -> CLOUD_GOLEM;
            case POSSESSED_PALADIN    -> POSSESSED_PALADIN;
            case OBLITERATOR          -> OBLITERATOR;
            case ENDER_GUARDIAN       -> ENDER_GUARDIAN;
            case NETHERITE_MONSTROSITY -> NETHERITE_MONSTROSITY;
        };
    }
}
