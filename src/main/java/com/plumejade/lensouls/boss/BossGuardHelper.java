package com.plumejade.lensouls.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * block_factorys_bosses 定身兼容判定（服务端）。
 * <p>
 * 判断实体是否属于 block_factorys_bosses 模组（任意该模组 boss，用于定身兼容分支）。
 */
public final class BossGuardHelper {

    private BossGuardHelper() {}

    /** 是否属于 block_factorys_bosses 模组（任意该模组 boss，用于定身兼容分支）。 */
    public static boolean isBlockFactorysBoss(LivingEntity entity) {
        return "block_factorys_bosses".equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace());
    }
}
