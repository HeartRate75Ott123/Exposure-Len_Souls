package com.plumejade.lensouls.ability.util;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 维度显示名。
 * <p>
 * 一律走标准翻译键 {@code dimension.<命名空间>.<路径>}：原版三个维度由原版语言文件提供
 * （lensouls 的 zh_cn/en_us 里也各补了一份，避免个别版本缺键），模组维度由各自 jar 提供
 * ——已核对：暮色森林 {@code dimension.twilightforest.twilight_forest} = 暮色森林、
 * 永恒星光 {@code dimension.eternal_starlight.starlight} = 永恒星光。
 * <p>
 * 没有翻译的维度回退成原始 id，不会给玩家看到一个裸键名。
 */
public final class DimensionNames {

    private DimensionNames() {
    }

    /** 维度 id 字符串 → 可显示名字（缺翻译时回退原始 id）。 */
    public static Component of(String dimensionId) {
        if (dimensionId == null || dimensionId.isEmpty()) return Component.literal("?");
        ResourceLocation id = ResourceLocation.tryParse(dimensionId);
        if (id == null) return Component.literal(dimensionId);
        return Component.translatableWithFallback("dimension." + id.getNamespace() + "." + id.getPath(), dimensionId);
    }
}
