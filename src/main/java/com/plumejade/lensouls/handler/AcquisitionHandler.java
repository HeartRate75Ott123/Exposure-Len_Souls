package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.entity.BossPhantomType;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * 物品获取方式处理器。
 * <p>
 * 通过 {@link LivingDropsEvent} 实现：
 * <ul>
 *   <li>基础镜魂：击杀怪物有 10% 概率掉落一个随机元素镜魂</li>
 *   <li>BOSS 镜魂：击杀对应 BOSS 必定掉落其专属镜魂</li>
 *   <li>能力球：击杀 BOSS 有 50% 概率掉落一个随机能力球</li>
 * </ul>
 * 所有掉落均可通过配置开关独立控制。
 */
public class AcquisitionHandler {

    /** 已注册的 BOSS 实体类型 ID 集合（含模组命名空间） */
    private static final Set<String> BOSS_ENTITY_IDS = new HashSet<>();

    static {
        for (BossPhantomType type : BossPhantomType.values()) {
            BOSS_ENTITY_IDS.add(type.getModId() + ":" + type.getEntityRegistryName());
        }
    }

    // ========== 基础镜魂掉落 ==========

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().level().isClientSide) return;

        // ---- 基础镜魂：击杀怪物 10% 掉落 ----
        if (Config.ENABLE_BASIC_SOUL_DROP.get()) {
            handleBasicSoulDrop(event);
        }

        // ---- BOSS 镜魂 + 能力球掉落 ----
        if (Config.ENABLE_BOSS_SOUL_DROP.get() || Config.ENABLE_SKILL_BALL_BOSS_LOOT.get()) {
            handleBossDrop(event);
        }
    }

    // ========== 基础镜魂 ==========

    private static void handleBasicSoulDrop(LivingDropsEvent event) {
        // 只处理怪物（Monster），排除 BOSS
        if (!(event.getEntity() instanceof Monster)) return;
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString();
        if (BOSS_ENTITY_IDS.contains(entityId)) return;

        // 10% 概率
        if (event.getEntity().getRandom().nextFloat() >= 0.1f) return;

        // 随机出四个基础元素镜魂之一
        ElementDamage[] basics = { ElementDamage.FIRE, ElementDamage.WATER, ElementDamage.EARTH, ElementDamage.ENDER };
        ElementDamage randomElement = basics[event.getEntity().getRandom().nextInt(basics.length)];

        ItemStack soulStack = switch (randomElement) {
            case FIRE -> new ItemStack(ModItems.FIRE_SOUL.get());
            case WATER -> new ItemStack(ModItems.WATER_SOUL.get());
            case EARTH -> new ItemStack(ModItems.EARTH_SOUL.get());
            case ENDER -> new ItemStack(ModItems.ENDER_SOUL.get());
            default -> ItemStack.EMPTY;
        };

        if (!soulStack.isEmpty()) {
            event.getDrops().add(createItemEntity(event, soulStack));
        }
    }

    // ========== BOSS 镜魂 + 能力球 ==========

    private static void handleBossDrop(LivingDropsEvent event) {
        var entity = event.getEntity();
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

        // 查找匹配的 BOSS 类型
        BossPhantomType bossType = null;
        for (BossPhantomType type : BossPhantomType.values()) {
            String bossId = type.getModId() + ":" + type.getEntityRegistryName();
            if (bossId.equals(entityId) && type.isModLoaded()) {
                bossType = type;
                break;
            }
        }

        if (bossType == null) return;

        // ---- BOSS 镜魂（必定掉落） ----
        if (Config.ENABLE_BOSS_SOUL_DROP.get()) {
            ItemStack bossSoul = getBossSoulStack(bossType);
            if (!bossSoul.isEmpty()) {
                event.getDrops().add(createItemEntity(event, bossSoul));
            }
        }

        // ---- 能力球（50% 概率） ----
        if (Config.ENABLE_SKILL_BALL_BOSS_LOOT.get() && entity.getRandom().nextFloat() < 0.5f) {
            event.getDrops().add(createItemEntity(event, new ItemStack(ModItems.SKILL_BALL.get())));
        }
    }

    /** 在死亡实体位置创建一个物品实体 */
    private static ItemEntity createItemEntity(LivingDropsEvent event, ItemStack stack) {
        var entity = event.getEntity();
        return new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
    }

    /** 根据 BOSS 类型返回对应的镜魂物品 */
    private static ItemStack getBossSoulStack(BossPhantomType type) {
        return switch (type) {
            case IGNIS -> new ItemStack(ModItems.IGNIS_SOUL.get());
            case CLOUD_GOLEM -> new ItemStack(ModItems.CLOUD_GOLEM_SOUL.get());
            case POSSESSED_PALADIN -> new ItemStack(ModItems.POSSESSED_PALADIN_SOUL.get());
            case OBLITERATOR -> new ItemStack(ModItems.OBLITERATOR_SOUL.get());
            case ENDER_GUARDIAN -> new ItemStack(ModItems.ENDER_GUARDIAN_SOUL.get());
            case NETHERITE_MONSTROSITY -> new ItemStack(ModItems.NETHERITE_MONSTROSITY_SOUL.get());
            case HYDRA -> new ItemStack(ModItems.HYDRA_SOUL.get());
            case KNIGHT_PHANTOM -> new ItemStack(ModItems.KNIGHT_PHANTOM_SOUL.get());
            case ALPHA_YETI -> new ItemStack(ModItems.ALPHA_YETI_SOUL.get());
            case NAGA -> new ItemStack(ModItems.NAGA_SOUL.get());
            case LAVA_EATER -> new ItemStack(ModItems.LAVA_EATER_SOUL.get());
            case THE_LEVIATHAN -> new ItemStack(ModItems.THE_LEVIATHAN_SOUL.get());
            case SCYLLA -> new ItemStack(ModItems.SCYLLA_SOUL.get());
        };
    }
}
