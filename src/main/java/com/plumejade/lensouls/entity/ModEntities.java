package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组实体类型注册表。
 */
public class ModEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LenSouls.MODID);

    /** BOSS 虚影幻灵（纯视觉效果，无 AI 无碰撞） */
    public static final DeferredHolder<EntityType<?>, EntityType<BossPhantomEntity>> BOSS_PHANTOM =
            ENTITIES.register("boss_phantom", () -> EntityType.Builder.<BossPhantomEntity>of(
                            BossPhantomEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .clientTrackingRange(64)
                    .updateInterval(2)
                    .noSave()
                    .noSummon()
                    .build("boss_phantom"));

    /** 次元枪子弹 */
    public static final DeferredHolder<EntityType<?>, EntityType<GunBulletEntity>> GUN_BULLET =
            ENTITIES.register("gun_bullet", () -> EntityType.Builder.<GunBulletEntity>of(
                            GunBulletEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("gun_bullet"));

    /** 引力枪子弹 */
    public static final DeferredHolder<EntityType<?>, EntityType<GravityBulletEntity>> GRAVITY_BULLET =
            ENTITIES.register("gravity_bullet", () -> EntityType.Builder.<GravityBulletEntity>of(
                            GravityBulletEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .build("gravity_bullet"));

    /** 扭曲者（惩罚性怪物，僵尸 AI 只攻击归属玩家） */
    public static final DeferredHolder<EntityType<?>, EntityType<TwitcherEntity>> TWITCHER =
            ENTITIES.register("twitcher", () -> EntityType.Builder.<TwitcherEntity>of(
                            TwitcherEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(8)
                    .build("twitcher"));

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    /** 扭曲者属性：基于僵尸基础属性（后续生成时按玩家状态覆写） */
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(TWITCHER.get(), Zombie.createAttributes().build());
    }
}
