package com.plumejade.lensouls.ability;

/**
 * 摄魂术四能力枚举。
 * <p>
 * 固定顺序用于循环切换：WEAKNESS_LENS → SPATIAL_WARP → TEMPORAL_RECALL → TIME_STOP
 */
public enum AbilityType {

    WEAKNESS_LENS("weakness_lens"),
    SPATIAL_WARP("spatial_warp"),
    TEMPORAL_RECALL("temporal_recall"),
    TIME_STOP("time_stop"),
    VITAL_STRIKE("vital_strike");

    private final String id;

    AbilityType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
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

    /**
     * 该能力是否需要在拍照时向照片注入额外数据。
     */
    public boolean injectsPhotoData() {
        return this == SPATIAL_WARP || this == TEMPORAL_RECALL;
    }
}
