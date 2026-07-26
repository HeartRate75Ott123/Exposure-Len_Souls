package com.plumejade.lensouls.damage;

/**
 * 元素伤害类型枚举。
 * <p>
 * FIRE, WATER, EARTH 通过隐藏状态效果激活，
 * PROJECTILE 由伤害来源自动识别（箭、三叉戟、雪球等）。
 * <p>
 * 扩展新元素参照 {@code memory/element-extension-guide.md}。
 */
public enum ElementDamage {
    FIRE("fire"),
    WATER("water"),
    EARTH("earth"),
    ENDER("ender"),
    PROJECTILE("projectile");

    private final String serializedName;

    ElementDamage(String serializedName) {
        this.serializedName = serializedName;
    }

    /**
     * 用于 JSON 序列化的名称（小写）。
     */
    public String getSerializedName() {
        return serializedName;
    }

    /**
     * 从 JSON 序列化名称解析枚举值。
     *
     * @param name 序列化名称（大小写不敏感）
     * @return 对应的枚举值，若无法识别返回 null
     */
    public static ElementDamage byName(String name) {
        for (ElementDamage element : values()) {
            if (element.serializedName.equalsIgnoreCase(name)) {
                return element;
            }
        }
        return null;
    }

    // ========== 活性和等级工具 ==========

    /** 等级对应的活性倍率（仅对非弹射物有效） */
    private static final float[] ACTIVITY_BY_LEVEL = {0, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f};

    /**
     * 等级 → 活性倍率
     * @param level 1~5，超出钳位
     */
    public static float getActivityByLevel(int level) {
        int clamped = Math.max(1, Math.min(5, level));
        return ACTIVITY_BY_LEVEL[clamped];
    }

    /**
     * effect amplifier → 活性倍率
     * amplifier 0→等级1(1.2x), 1→2(1.5x), 2→3(2.0x), 3→4(2.5x), 4→5(3.0x)
     */
    public static float getActivityByAmplifier(int amplifier) {
        return getActivityByLevel(amplifier + 1);
    }

    /**
     * 活性倍率 → 等级
     */
    public static int getLevelByActivity(float activity) {
        for (int i = ACTIVITY_BY_LEVEL.length - 1; i >= 1; i--) {
            if (activity >= ACTIVITY_BY_LEVEL[i] - 0.01f) return i;
        }
        return 1;
    }
}
