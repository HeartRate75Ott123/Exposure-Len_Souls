package com.plumejade.lensouls.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 照片 Boss 弹幕命中后清空目标无敌帧：被标记 {@code lensouls:photo_proj} 的弹射物命中时，
 * 将目标 invulnerableTime 清零，使多段判伤技能（激光/符文/尖刺等）能全额连续造成伤害。
 * <p>
 * 会对目标造成「最大生命百分比」伤害的照片弹幕（标记 {@code lensouls:photo_percent}，
 * 如湮灭激光 5%/tick、云筑激光 1%/tick、利维坦射线、焰魔火球）额外施加 <b>10 tick 内置间隔</b>：
 * 同一弹幕对同一目标 10 tick 内不再清无敌帧 → 原版 20tick 无敌帧把连续结算收敛为
 * "同目标每 10tick 至多一次"，避免百分比伤害每 tick 全额叠加。
 */
@Mixin(LivingEntity.class)
public abstract class BossProjHurtMixin {

    /** 百分比弹幕同目标清帧间隔（tick） */
    private static final int PERCENT_INTERVAL = 10;

    /** key=(弹幕实体Id<<32)|目标Id → 最近一次清帧 gameTime（仅服务端；超量自动清理） */
    private static final Map<Long, Long> LAST_CLEAR = new HashMap<>();

    @Inject(method = "hurt", at = @At("TAIL"))
    private void lensouls$clearInvulnForPhotoProj(DamageSource source, float amount,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide) return;

        Entity direct = source.getDirectEntity();
        if (direct == null || !direct.getPersistentData().getBoolean("lensouls:photo_proj")) return;

        // 百分比伤害弹幕：同(弹幕,目标)施加 10tick 内置间隔
        if (direct.getPersistentData().getBoolean("lensouls:photo_percent")) {
            long now = victim.level().getGameTime();
            long key = ((long) direct.getId() << 32) | (victim.getId() & 0xFFFFFFFFL);
            Long last = LAST_CLEAR.get(key);
            if (last != null && now - last < PERCENT_INTERVAL) {
                return; // 间隔内：不清理 → 原版无敌帧挡掉本 tick 结算
            }
            LAST_CLEAR.put(key, now);
            if (LAST_CLEAR.size() > 256) {
                prune(now - 40);
            }
        }

        victim.invulnerableTime = 0;
    }

    /** 清理早于阈值的记录，防弹幕频繁触发导致泄漏 */
    private static void prune(long olderThan) {
        Iterator<Map.Entry<Long, Long>> it = LAST_CLEAR.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < olderThan) it.remove();
        }
    }
}
