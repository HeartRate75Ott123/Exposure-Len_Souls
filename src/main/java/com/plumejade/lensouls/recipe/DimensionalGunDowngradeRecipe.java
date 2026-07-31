package com.plumejade.lensouls.recipe;

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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * 次元枪满级降级配方（无序）：
 * 满级次元枪 + 龙首(minecraft:dragon_head) → 满击杀次元枪（移除 Maxed 标志）。
 * <p>
 * 始终可用（不依赖 eternal_starlight 模组）。
 */
public class DimensionalGunDowngradeRecipe extends CustomRecipe {

    public DimensionalGunDowngradeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack gun = null;
        boolean dragonHead = false;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DimensionalGunItem) {
                if (gun != null) return false;
                gun = stack;
            } else if (stack.is(Items.DRAGON_HEAD)) {
                dragonHead = true;
            } else {
                return false;
            }
        }
        if (gun == null || !dragonHead) return false;
        return gun.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("Maxed");
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
        ((DimensionalGunItem) result.getItem()).setMaxed(result, false);
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
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DimensionalGunRecipes.GUN_DOWNGRADE.get();
    }
}
