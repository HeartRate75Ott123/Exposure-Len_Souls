package com.plumejade.lensouls.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * block_factorys_bosses 定身兼容判定（服务端）。
 * <p>
 * 仅「5 个真 boss」需要专属定身处理（其转阶段逻辑写在重写的 tick 内，由
 * {@code BlockFactorysBossStunMixin} 整段暂停）；其余该模组生物（小怪/召唤物）
 * 交给通用 {@code BossStunTickMixin} 冻结，因此这里只按实体 ID 命中 boss 白名单，
 * 不能用命名空间一刀切（否则小怪会被通用定格一并排除，出现"定格后仍会动"）。
 */
public final class BossGuardHelper {

    /** block_factorys_bosses 5 个真 boss 实体 ID（不含剧情/召唤物） */
    private static final java.util.Set<String> BOSS_IDS = java.util.Set.of(
            "block_factorys_bosses:yeti",
            "block_factorys_bosses:underworld_knight",
            "block_factorys_bosses:infernal_dragon",
            "block_factorys_bosses:kraken",
            "block_factorys_bosses:sandworm"
    );

    private BossGuardHelper() {}

    /** 是否属于 block_factorys_bosses 的 5 个真 boss（需专属定身兼容分支）。 */
    public static boolean isBlockFactorysBoss(LivingEntity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return BOSS_IDS.contains(key.toString());
    }
}

