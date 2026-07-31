package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.item.DimensionalGunItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 次元枪满级升级配方（无序）：
 * 满击杀的次元枪 + 坚韧月藤(eternal_starlight:tenacious_vine) + 氧化傀儡钢锭(eternal_starlight:oxidized_golem_steel_ingot)
 * → 满级次元枪（Maxed 标志，+20 伤害）。
 * <p>
 * 仅当 eternal_starlight 模组加载时由 JSON 条件启用。
 */
public class DimensionalGunUpgradeRecipe extends CustomRecipe {

    public static final String VINE_ID = "eternal_starlight:tenacious_vine";
    public static final String INGOT_ID = "eternal_starlight:oxidized_golem_steel_ingot";

    public DimensionalGunUpgradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack gun = null;
        boolean vine = false, ingot = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DimensionalGunItem) {
                if (gun != null) return false;
                gun = stack;
            } else {
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.equals(VINE_ID)) vine = true;
                else if (id.equals(INGOT_ID)) ingot = true;
                else return false;
            }
        }
        if (gun == null || !vine || !ingot) return false;
        if (gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("Maxed")) return false;
        return gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("Kills") >= Config.DG_KILL_TARGET.get();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack gun = null;
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof DimensionalGunItem) {
                gun = stack;
                break;
            }
        }
        if (gun == null) return ItemStack.EMPTY;
        ItemStack result = gun.copy();
        result.setCount(1);
        ((DimensionalGunItem) result.getItem()).setMaxed(result, true);
        return result;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem().hasCraftingRemainingItem()) {
                remaining.set(i, new ItemStack(stack.getItem().getCraftingRemainingItem()));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DimensionalGunRecipes.GUN_UPGRADE.get();
    }
}
