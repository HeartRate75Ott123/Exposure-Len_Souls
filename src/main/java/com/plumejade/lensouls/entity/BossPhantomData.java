package com.plumejade.lensouls.entity;

import java.util.UUID;

/**
 * 虚影幻灵序列的运行时状态记录。
 */
public record BossPhantomData(
        BossPhantomType type,
        UUID playerId,
        int totalTicks,
        int remainingTicks,
        String descId,
        int phantomEntityId,
        int amplifier,
        double originX, double originY, double originZ,
        float originYRot, float originXRot,
        double watchX, double watchY, double watchZ,
        boolean spectatorRestored
) {

    public boolean isSkillTick() {
        return remainingTicks == type.getSkillTick();
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public BossPhantomData tick() {
        return new BossPhantomData(type, playerId, totalTicks, remainingTicks - 1,
                descId, phantomEntityId, amplifier, originX, originY, originZ, originYRot, originXRot,
                watchX, watchY, watchZ, spectatorRestored);
    }

    public BossPhantomData withSpectatorRestored(boolean value) {
        return new BossPhantomData(type, playerId, totalTicks, remainingTicks,
                descId, phantomEntityId, amplifier, originX, originY, originZ, originYRot, originXRot,
                watchX, watchY, watchZ, value);
    }
}
