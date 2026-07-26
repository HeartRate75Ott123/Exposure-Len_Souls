package com.plumejade.lensouls.ability.client;

import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * BOSS 镜魂物品渲染状态管理。
 * <p>
 * 维护活跃 BOSS 配色，供 {@link ItemRendererMixin} 和 {@link SoulGlowLayer} 使用。
 * 不持有延迟 buffer — 发光数据直接通过 {@code MultiBufferSource.immediate()} 立即渲染。
 */
@OnlyIn(Dist.CLIENT)
public class BossSoulItemState {

    private static final ThreadLocal<BossOutlineColors> ACTIVE_COLORS = ThreadLocal.withInitial(() -> null);

    // ========== 活跃状态 ==========

    public static void setActive(BossOutlineColors colors) {
        ACTIVE_COLORS.set(colors);
    }

    public static void clearActive() {
        ACTIVE_COLORS.set(null);
    }

    public static boolean isActive() {
        return ACTIVE_COLORS.get() != null;
    }

    public static BossOutlineColors getColors() {
        return ACTIVE_COLORS.get();
    }
}
