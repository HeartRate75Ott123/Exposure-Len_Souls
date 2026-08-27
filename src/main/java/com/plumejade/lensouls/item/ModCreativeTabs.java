package com.plumejade.lensouls.item;
import net.minecraft.world.item.component.CustomData;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.GunAmmoData;
import com.plumejade.lensouls.component.GunKillData;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组创造模式标签页。
 */
public class ModCreativeTabs {

    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LenSouls.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LENSOULS_TAB =
            TABS.register("lensouls_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lensouls"))
                    .icon(() -> new ItemStack(ModItems.FIRE_SOUL.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.FIRE_SOUL.get());
                        output.accept(ModItems.WATER_SOUL.get());
                        output.accept(ModItems.EARTH_SOUL.get());
                        output.accept(ModItems.ENDER_SOUL.get());
                        output.accept(ModItems.CONVERTER.get());

                        // ---- 能力球 ----
                        output.accept(ModItems.SKILL_BALL.get());
                        output.accept(ModItems.WEAKNESS_LENS_BALL.get());
                        output.accept(ModItems.SPATIAL_WARP_BALL.get());
                        output.accept(ModItems.TEMPORAL_RECALL_BALL.get());
                        output.accept(ModItems.TIME_STOP_BALL.get());
                        output.accept(ModItems.VITAL_STRIKE_BALL.get());
                        output.accept(ModItems.SOUL_SEVER_BALL.get());
                        output.accept(ModItems.ABILITY_STEAL_BALL.get());

                        output.accept(ModItems.LENS_TIER_1.get());
                        output.accept(ModItems.LENS_TIER_2.get());
                        output.accept(ModItems.LENS_TIER_3.get());
                        output.accept(ModItems.LENS_TIER_4.get());

                        // ---- 相机镜头配件 ----
                        output.accept(ModItems.LENS_TIER_1.get());
                        output.accept(ModItems.LENS_TIER_2.get());
                        output.accept(ModItems.LENS_TIER_3.get());
                        output.accept(ModItems.LENS_TIER_4.get());

                        // BOSS 专属镜魂
                        output.accept(ModItems.IGNIS_SOUL.get());
                        output.accept(ModItems.CLOUD_GOLEM_SOUL.get());
                        output.accept(ModItems.POSSESSED_PALADIN_SOUL.get());
                        output.accept(ModItems.OBLITERATOR_SOUL.get());
                        output.accept(ModItems.ENDER_GUARDIAN_SOUL.get());
                        output.accept(ModItems.NETHERITE_MONSTROSITY_SOUL.get());
                        output.accept(ModItems.HYDRA_SOUL.get());
                        output.accept(ModItems.KNIGHT_PHANTOM_SOUL.get());
                        output.accept(ModItems.ALPHA_YETI_SOUL.get());
                        output.accept(ModItems.NAGA_SOUL.get());
                        output.accept(ModItems.LAVA_EATER_SOUL.get());
                        output.accept(ModItems.THE_LEVIATHAN_SOUL.get());
                        output.accept(ModItems.SCYLLA_SOUL.get());


                        // ---- 枪械 ----
                        output.accept(ModItems.DIMENSIONAL_GUN.get());
                        // 满级次元枪（方便测试）
                        ItemStack maxGun = new ItemStack(ModItems.DIMENSIONAL_GUN.get());
                        CompoundTag maxTag = maxGun.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                        maxTag.putInt("Kills", Config.DG_KILL_TARGET.get());
                        maxTag.putInt("MaxAmmo", Config.DG_MAX_AMMO.get());
                        maxTag.putInt("Ammo", Config.DG_MAX_AMMO.get());
                        maxTag.putInt("UnlockedAmmos", 0b111);
                        maxGun.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(maxTag));
                        maxGun.set(ModDataComponents.GUN_AMMO.get(), new GunAmmoData(Config.DG_MAX_AMMO.get(), Config.DG_MAX_AMMO.get()));
                        maxGun.set(ModDataComponents.GUN_KILLS.get(), new GunKillData(Config.DG_KILL_TARGET.get()));
                        output.accept(maxGun);

                        output.accept(ModItems.GRAVITY_GUN.get());

                        // ---- 回复药水 ----
                        output.accept(ModItems.HEAL_POTION.get());

                        // ---- 复制之魂 ----
                        output.accept(ModItems.COPY_SOUL.get());

                        // ---- 羽·元素觉醒者 ----
                        output.accept(ModItems.FEATHER_ELEMENTRISE.get());

                        // ---- 羽·扭曲之人 ----
                        output.accept(ModItems.FEATHER_TWITCHER.get());

                        // ---- 羽·荒厄遗咒 ----
                        output.accept(ModItems.FEATHER_HARDMAN.get());

                        // ---- 羽·折翼沉渊 ----
                        output.accept(ModItems.FEATHER_ABYSS.get());

                        // 摄魂术附魔书（通过参数查找注册表避免闪退）
                        var enchants = params.holders()
                                .lookupOrThrow(Registries.ENCHANTMENT);
                        var holder = enchants.getOrThrow(
                                ModEnchantments.SOUL_PHOTOGRAPHY_KEY);
                        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                        book.enchant(holder, 1);
                        output.accept(book);

                        // 实体照片（供 JEI 搜索）
                        for (String eid : com.plumejade.lensouls.integration.PhotographEffectRegistry.getAllEntityIds()) {
                            output.accept(com.plumejade.lensouls.item.EntityPhotographItem.create(eid));
                        }
                    })
                    .build());

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
