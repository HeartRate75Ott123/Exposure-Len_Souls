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

    /**
     * 等级 → 活性倍率：等级 0 = 无活性，等级 1 起活性 1.0，每加一级 +0.5。
     * <pre>
     * level 0   → 0.0（无活性）
     * level 1   → 1.0
     * level 1.5 → 1.25
     * level 2   → 1.5
     * level 5   → 3.0
     * level 9   → 5.0
     * </pre>
     *
     * @param level 活性等级（0~9，允许 0.5 步进，负值按 0 处理）
     */
    public static float getActivityByLevel(float level) {
        if (level <= 0f) return 0f;
        return 1f + (level - 1f) * 0.5f;
    }

    /**
     * effect amplifier → 活性倍率
     * amplifier 0 → 等级1(1.0x), 1 → 2(1.5x), 2 → 3(2.0x), 3 → 4(2.5x), 4 → 5(3.0x)
     */
    public static float getActivityByAmplifier(int amplifier) {
        return getActivityByLevel(amplifier + 1);
    }
}
