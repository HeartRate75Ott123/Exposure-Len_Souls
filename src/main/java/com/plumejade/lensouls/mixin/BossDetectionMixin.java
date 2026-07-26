package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.boss.BossBarCache;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * BOSS 检测 Mixin。
 * <p>
 * 在 {@link Mob} 构造时扫描类的字段，检测是否包含 {@link ServerBossEvent} 类型字段。
 * 任何拥有 BOSS 血条字段的实体自动视为 BOSS。
 * <p>
 * 检测结果写入 {@link BossBarCache}，供业务逻辑读取。
 * 按 Class 粒度缓存，每类只反射扫描一次。
 */
@Mixin(Mob.class)
public class BossDetectionMixin {

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("RETURN"))
    private void lensouls$scanBossBarField(CallbackInfo ci) {
        Class<?> clazz = getClass();
        // 已在缓存中（无论 BOSS 与否），跳过重复扫描
        if (BossBarCache.isScanned(clazz)) return;

        boolean hasBossBar = scanClassHierarchy(clazz);
        BossBarCache.markScanned(clazz, hasBossBar);

        if (hasBossBar) {
        }
    }

    /**
     * 从当前类向上扫描到 {@link Mob}，查找 {@link ServerBossEvent} 类型字段。
     */
    @Unique
    private static boolean scanClassHierarchy(Class<?> clazz) {
        Class<?> scan = clazz;
        while (scan != null && scan != Mob.class) {
            for (Field field : scan.getDeclaredFields()) {
                if (ServerBossEvent.class.isAssignableFrom(field.getType())) {
                    return true;
                }
            }
            scan = scan.getSuperclass();
        }
        return false;
    }
}
