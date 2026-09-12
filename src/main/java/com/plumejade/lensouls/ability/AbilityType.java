package com.plumejade.lensouls.ability;

import net.minecraft.resources.ResourceLocation;

/**
 * 摄魂术能力枚举。
 * <p>
 * 声明顺序即 GUI 中卡片展示顺序（新能力追加枚举即可自动收纳）。
 * <p>
 * <b>严禁在中间插入常量</b>：{@code PlayerAbilityData} 的解锁位图与存档里的
 * {@code currentOrdinal} 都按 ordinal 记录，插队会让老存档的能力解锁状态整体错位。
 * 新能力一律追加到末尾。
 */
public enum AbilityType {

    WEAKNESS_LENS("weakness_lens"),
    SPATIAL_WARP("spatial_warp"),
    TEMPORAL_RECALL("temporal_recall"),
    TIME_STOP("time_stop"),
    VITAL_STRIKE("vital_strike"),
    SOUL_SEVER("soul_sever"),
    ABILITY_STEAL("ability_steal"),
    /** 见微知著：拍照记录画面中的生物 → 解锁图鉴条目，并赋予随机持久属性增益 */
    WILD_GLIMPSE("wild_glimpse");

    private final String id;

    AbilityType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    /** 能力显示名翻译键：ability.lensouls.{id}.name */
    public String getNameKey() {
        return "ability.lensouls." + id + ".name";
    }

    /** 能力简介翻译键（首次解锁播报/卡片简介用） */
    public String getDescriptionKey() {
        return "ability.lensouls." + id + ".description";
    }

    /** 能力长文案翻译键（GUI 详情介绍框用） */
    public String getDetailKey() {
        return "ability.lensouls." + id + ".detail";
    }

    /** 能力图标（占位用能力球物品）：lensouls:{id}_ball */
    public ResourceLocation getIconItemId() {
        return ResourceLocation.fromNamespaceAndPath("lensouls", id + "_ball");
    }

    /**
     * 按 ID 查找能力（大小写不敏感）。
     */
    public static AbilityType byId(String id) {
        for (AbilityType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }

    /**
     * 该能力是否需要摄魂术附魔。
     * 所有能力都需要附魔（包括弱点透镜），无附魔时静默跳过。
     */
    public boolean requiresEnchantment() {
        return true;
    }
}
