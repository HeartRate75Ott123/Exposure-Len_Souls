package com.plumejade.lensouls.client.outline;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * BOSS 镜魂描边配色（四元素四套渐变，大众审美、绚丽、不深色）。
 * <p>
 * 第三人称全身描边走原版 outline（{@code isCurrentlyGlowing}/{@code getTeamColor}），
 * 由渐变 composite shader 读取 {@link #MARKER_COLOR} 标记色后替换为四色渐变；
 * 第一人称手持物发光（SoulGlow）也直接用这四套渐变。
 *
 * @param color1 主色 R/G/B
 * @param color2 次色 R/G/B
 * @param color3 第三色 R/G/B
 * @param color4 第四色 R/G/B
 * @param glowStrength 发光强度
 * @param outlineWidth 描边宽度
 */
public record BossOutlineColors(
        float[] color1, float[] color2,
        float[] color3, float[] color4,
        float glowStrength, float outlineWidth) {

    /** 原版 outline buffer 中的标记色（玩家 BOSS 镜魂轮廓），渐变 composite shader 据此识别并替换为四色渐变 */
    public static final int MARKER_COLOR = 0xFE00FE;
    public static final float[] MARKER_RGB = rgb(0xFE, 0x00, 0xFE);

    // ============ 四元素渐变配色 ============

    /** 土元素 — 绿色系渐变 */
    public static final BossOutlineColors EARTH = new BossOutlineColors(
            rgb(0.55f, 1.0f, 0.35f),
            rgb(0.35f, 1.0f, 0.60f),
            rgb(0.75f, 1.0f, 0.25f),
            rgb(0.45f, 1.0f, 0.75f),
            1.2f, 3.0f
    );

    /** 水元素 — 蓝色系渐变 */
    public static final BossOutlineColors WATER = new BossOutlineColors(
            rgb(0.35f, 0.85f, 1.0f),
            rgb(0.55f, 0.95f, 1.0f),
            rgb(0.30f, 0.70f, 1.0f),
            rgb(0.45f, 1.0f, 1.0f),
            1.2f, 3.0f
    );

    /** 火元素 — 红色/橙系渐变 */
    public static final BossOutlineColors FIRE = new BossOutlineColors(
            rgb(1.0f, 0.40f, 0.30f),
            rgb(1.0f, 0.60f, 0.25f),
            rgb(1.0f, 0.30f, 0.45f),
            rgb(1.0f, 0.75f, 0.30f),
            1.2f, 3.0f
    );

    /** 末影元素 — 紫色系渐变 */
    public static final BossOutlineColors ENDER = new BossOutlineColors(
            rgb(0.80f, 0.40f, 1.0f),
            rgb(0.65f, 0.45f, 1.0f),
            rgb(0.95f, 0.50f, 1.0f),
            rgb(0.75f, 0.55f, 1.0f),
            1.2f, 3.0f
    );

    // ========== 工厂方法 ==========

    private static float[] rgb(float r, float g, float b) {
        return new float[]{r, g, b};
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r / 255f, g / 255f, b / 255f};
    }

    /** 从任意活跃实体的元素附魔效果检测 BOSS 类型并返回配色。颜色只按镜魂元素四套渐变，不按 BOSS/等级。 */
    public static BossOutlineColors fromEntity(LivingEntity entity) {
        if (entity == null) return null;
        BossPhantomType bestType = null;
        ElementDamage bestElement = null;
        int bestDuration = -1;

        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof ElementInfusionEffect effect) {
                ElementDamage element = effect.getElement();
                String descId = ElementInfusionEffect.getPlayerCustomName(
                        (entity instanceof Player p) ? p : null, element);
                if (descId != null && !descId.isEmpty()) {
                    BossPhantomType matched = BossPhantomType.fromDescriptionId(descId);
                    if (matched != null) {
                        int dur = inst.getDuration();
                        if (dur > bestDuration) {
                            bestDuration = dur;
                            bestType = matched;
                            bestElement = element;
                        }
                    }
                }
            }
        }

        if (bestType != null) {
            return bestElement != null ? fromElement(bestElement) : null;
        }
        return null;
    }

    public static BossOutlineColors fromPlayer(Player player) {
        return fromEntity(player);
    }

    /** 按元素返回四套渐变描边（土绿/水蓝/火红/末影紫） */
    public static BossOutlineColors fromElement(ElementDamage element) {
        return switch (element) {
            case EARTH -> EARTH;
            case WATER -> WATER;
            case FIRE -> FIRE;
            case ENDER -> ENDER;
            default -> null;
        };
    }
}
