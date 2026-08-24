package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.config.CopySoulFilter;
import com.plumejade.lensouls.item.CopySoulItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 复制之魂复制配方（工作台，无序）：
 * 1 个复制之魂 + 1 种任意物品 → 输出该物品的完整副本。
 * <p>
 * 输出经 {@link #assemble} 按输入动态生成，完整保留附魔、数据组件与 NBT；
 * 消耗由 {@link #getRemainingItems} 控制：复制之魂正常消耗，
 * 原物品槽位返回 1 份原物品副本（工作台每槽 removeItem(1) 后 remaining 放回原槽）→ 原物品不消耗。
 */
public class CopySoulRecipe extends CustomRecipe {

    public CopySoulRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean hasSoul = false;
        ItemStack target = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof CopySoulItem) {
                if (hasSoul) return false;
                hasSoul = true;
            } else {
                if (target != null) return false;
                target = stack;
            }
        }
        if (!hasSoul || target == null) return false;
        // 复制之魂本身不可复制；数据驱动复制黑白名单
        if (target.getItem() instanceof CopySoulItem) return false;
        if (!CopySoulFilter.isCopyAllowed(BuiltInRegistries.ITEM.getKey(target.getItem()))) return false;
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // 动态输出：原物品完整副本（组件/NBT/附魔/数量全保留）
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty() && !(stack.getItem() instanceof CopySoulItem)
                    && CopySoulFilter.isCopyAllowed(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return stack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 只消耗复制之魂：原物品槽位返回 1 份原物品（含全部组件/NBT），
     * 工作台取出时先 removeItem(1) 再将该份放回原槽，实现原物品保留。
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof CopySoulItem) continue; // 复制之魂消耗
            ItemStack keep = stack.copy();
            keep.setCount(1);
            remaining.set(i, keep);
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return CopySoulRecipes.COPY_SOUL.get();
    }
}
