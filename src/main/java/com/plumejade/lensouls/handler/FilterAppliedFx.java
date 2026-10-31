package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.network.FilterAppliedFxPacket;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 药水玻璃板滤镜施加成功 → 世界特效「对焦收束」广播（服务端唯一出口）。
 * <p>
 * 特效本身全在客户端（{@code WorldFxBursts.FilterAppliedBurst}），这里只负责
 * "告诉追踪这个实体的玩家：它被上了一层滤镜，颜色是这个"。颜色取第一条药水效果的 {@code getColor()}。
 * <p>
 * <b>只服务药水玻璃板</b>：16 款特殊滤镜的反馈是"图腾式物品弹出"
 * （{@code FilterPhotoHandler} → {@code FilterItemPopupPacket}），不再走这里——
 * 那是用户定的分工（玻璃板＝法印管线，特殊滤镜＝物品弹出）。
 */
public final class FilterAppliedFx {

    private FilterAppliedFx() {
    }

    /** @param rgb 0xRRGGBB（-1 / 0 视为无颜色 → 退回银白） */
    public static void broadcast(LivingEntity target, int rgb) {
        if (target == null || target.level().isClientSide()) return;

        int packed = rgb;
        if (packed == 0 || packed == -1) packed = 0xCFE4FF;
        float r = ((packed >> 16) & 0xFF) / 255.0F;
        float g = ((packed >> 8) & 0xFF) / 255.0F;
        float b = (packed & 0xFF) / 255.0F;

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new FilterAppliedFxPacket(target.getId(), r, g, b));
    }
}
