package com.plumejade.lensouls.client.outline;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.SoulDotEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * BOSS 镜魂描边配色（四元素四套渐变，大众审美、绚丽、不深色）。
 * <p>
 * 描边信号 = 玩家身上有 BOSS 镜魂的镜魂 DoT 效果（{@link SoulDotEffect}）；
 * 第三人称全身描边走原版 outline（{@code isCurrentlyGlowing}/{@code getTeamColor}）取主色，
 * 第一人称手持物描边（{@code client/itemoutline} 独立 FBO 管线）同样经 {@link #fromPlayer} 判定。
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

    // ============ 四元素配色（取主色做单色描边） ============

    /** 土元素 — 绿色 */
    public static final BossOutlineColors EARTH = new BossOutlineColors(
            rgb(0.45f, 1.0f, 0.35f),
            rgb(0.35f, 1.0f, 0.60f),
            rgb(0.75f, 1.0f, 0.25f),
            rgb(0.45f, 1.0f, 0.75f),
            1.2f, 3.0f
    );

    /** 水元素 — 蓝色 */
    public static final BossOutlineColors WATER = new BossOutlineColors(
            rgb(0.35f, 0.85f, 1.0f),
            rgb(0.55f, 0.95f, 1.0f),
            rgb(0.30f, 0.70f, 1.0f),
            rgb(0.45f, 1.0f, 1.0f),
            1.2f, 3.0f
    );

    /** 火元素 — 红色 */
    public static final BossOutlineColors FIRE = new BossOutlineColors(
            rgb(1.0f, 0.40f, 0.30f),
            rgb(1.0f, 0.60f, 0.25f),
            rgb(1.0f, 0.30f, 0.45f),
            rgb(1.0f, 0.75f, 0.30f),
            1.2f, 3.0f
    );

    /** 末影元素 — 紫色 */
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

    /** 主色（0xRRGGBB），单色描边 / 物品发光用 */
    public int primaryColor() {
        int r = (int) (color1[0] * 255f) & 0xFF;
        int g = (int) (color1[1] * 255f) & 0xFF;
        int b = (int) (color1[2] * 255f) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 描边信号源：只认镜魂 DoT 效果（{@link SoulDotEffect}）且当前激活的是 BOSS 镜魂
     * （{@link SoulDotEffect#getPlayerSoulId} 非空）。照片活性走元素灌注效果，
     * 永远不会触发描边；第三人称全身 glow 与第一人称手持物描边共用本判定。
     */
    public static BossOutlineColors fromEntity(LivingEntity entity) {
        if (entity == null) return null;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect().value() instanceof SoulDotEffect effect) {
                String soulId = SoulDotEffect.getPlayerSoulId(entity, effect.getElement());
                if (soulId != null && !soulId.isEmpty()) {
                    return fromElement(effect.getElement());
                }
            }
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
