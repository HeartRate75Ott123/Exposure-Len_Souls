package com.plumejade.lensouls.integration.jei;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.item.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 兼容插件。
 * <p>
 * 为模组物品添加获取方式说明。
 * 所有配方由 JEI 自动从 data/recipe/ 发现并显示。
 */
@JeiPlugin
public class LensoulsJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.ENTITY_PHOTOGRAPH.get(), (stack, ctx) -> {
            String entity = PhotographEffectRegistry.getStolenEntity(stack);
            return entity != null ? entity : "none";
        });
    }

    /** 铁砧升级提示（绿色） */
    private static final Component ANVIL_UPGRADE =
            Component.translatable("jei.lensouls.soul.anvil_upgrade").copy().withStyle(ChatFormatting.DARK_GRAY);

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // ---- 基础镜魂 ----
        addSoulInfo(registration, ModItems.FIRE_SOUL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.soul.basic"));
        addSoulInfo(registration, ModItems.WATER_SOUL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.soul.basic"));
        addSoulInfo(registration, ModItems.EARTH_SOUL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.soul.basic"));
        addSoulInfo(registration, ModItems.ENDER_SOUL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.soul.basic"));

        // ---- BOSS 镜魂（BOSS 名用绿色） ----
        addSoulInfo(registration, ModItems.IGNIS_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.cataclysm.ignis"));
        addSoulInfo(registration, ModItems.CLOUD_GOLEM_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.legendary_monsters.cloud_golem"));
        addSoulInfo(registration, ModItems.POSSESSED_PALADIN_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.legendary_monsters.posessed_paladin"));
        addSoulInfo(registration, ModItems.OBLITERATOR_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.legendary_monsters.the_obliterator"));
        addSoulInfo(registration, ModItems.ENDER_GUARDIAN_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.cataclysm.ender_guardian"));
        addSoulInfo(registration, ModItems.NETHERITE_MONSTROSITY_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.cataclysm.netherite_monstrosity"));

        // ---- 暮色 BOSS 镜魂 ----
        addSoulInfo(registration, ModItems.HYDRA_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.twilightforest.hydra"));
        addSoulInfo(registration, ModItems.KNIGHT_PHANTOM_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.twilightforest.knight_phantom"));
        addSoulInfo(registration, ModItems.ALPHA_YETI_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.twilightforest.alpha_yeti"));
        addSoulInfo(registration, ModItems.NAGA_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.twilightforest.naga"));
        // ---- 传奇怪物 ----
        addSoulInfo(registration, ModItems.LAVA_EATER_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.legendary_monsters.lava_eater"));
        // ---- 灾变 ----
        addSoulInfo(registration, ModItems.THE_LEVIATHAN_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.cataclysm.the_leviathan"));
        addSoulInfo(registration, ModItems.SCYLLA_SOUL.get().getDefaultInstance(),
                bossSoulInfo("entity.cataclysm.scylla"));

        // ---- 能力球 ----
        // 随机能力球：随配置动态显示——禁用 boss 掉落时自动隐藏该条目，概率实时取自配置
        if (Config.ENABLE_SKILL_BALL_BOSS_LOOT.get()) {
            int chance = Math.round((float) (double) Config.SKILL_BALL_DROP_CHANCE.get() * 100.0f);
            addSingleInfo(registration, ModItems.SKILL_BALL.get().getDefaultInstance(),
                    Component.translatable("jei.lensouls.skill_ball", chance));
        }
        addSingleInfo(registration, ModItems.WEAKNESS_LENS_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.SPATIAL_WARP_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.TEMPORAL_RECALL_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.TIME_STOP_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.VITAL_STRIKE_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.SOUL_SEVER_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));
        addSingleInfo(registration, ModItems.ABILITY_STEAL_BALL.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.skill_ball.creative"));

        // ---- 相机镜头 ----
        addSingleInfo(registration, ModItems.LENS_TIER_1.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.lens_tier_1"));
        addSingleInfo(registration, ModItems.LENS_TIER_2.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.lens_tier_2"));
        addSingleInfo(registration, ModItems.LENS_TIER_3.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.lens_tier_3"));
        addSingleInfo(registration, ModItems.LENS_TIER_4.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.lens_tier_4"));

        // ---- 转换器 ----
        addSingleInfo(registration, ModItems.CONVERTER.get().getDefaultInstance(),
                Component.translatable("jei.lensouls.converter"));

        // ---- 能力窃取实体照片（可搜索，不加入创造选项卡） ----
        for (String entityId : PhotographEffectRegistry.getAllEntityIds()) {
            ItemStack photo = com.plumejade.lensouls.item.EntityPhotographItem.create(entityId);
            Component entityName = Component.translatable(
                    PhotographEffectRegistry.entityIdToTranslationKey(entityId));
            registration.addIngredientInfo(photo, VanillaTypes.ITEM_STACK,
                    Component.translatable("jei.lensouls.photograph_curio", entityName));
        }
    }

    /** 为物品添加一条信息提示（varargs 支持多行） */
    private static void addInfo(IRecipeRegistration registration, ItemStack stack, Component... descriptions) {
        registration.addIngredientInfo(stack, VanillaTypes.ITEM_STACK, descriptions);
    }

    /** 为镜魂物品添加获取方式 + 铁砧升级提示（合并到同一次调用） */
    private static void addSoulInfo(IRecipeRegistration registration, ItemStack soulStack, Component acquisitionInfo) {
        addInfo(registration, soulStack, acquisitionInfo, ANVIL_UPGRADE);
    }

    /** 构建 BOSS 镜魂信息：BOSS 名绿色，其他默认 */
    private static Component bossSoulInfo(String bossEntityKey) {
        Component bossName = Component.translatable(bossEntityKey).copy().withStyle(ChatFormatting.GREEN);
        return Component.translatable("jei.lensouls.soul.boss", bossName);
    }

    /** 为能力球等单行物品添加提示 */
    private static void addSingleInfo(IRecipeRegistration registration, ItemStack stack, Component description) {
        addInfo(registration, stack, description);
    }
}
