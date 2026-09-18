package com.plumejade.lensouls.recipe;

import com.plumejade.lensouls.reinforce.ReinforceDataLoader;
import com.plumejade.lensouls.reinforce.ReinforceHelper;
import com.plumejade.lensouls.reinforce.ReinforceMaterial;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 工作台强化配方（无序，任意物品 + 1 个强化材料）：
 * <pre>
 *   被强化物品 + 强化材料  →  被强化物品的完整副本（数量、NBT、数据组件全保留，附加材料属性）
 * </pre>
 * <ul>
 *     <li>被强化物品：任意物品，但数据包黑名单（{@code reinforcement/blacklist}）中的物品除外；</li>
 *     <li>强化材料：数据包 {@code reinforcement} 中配置过的物品；</li>
 *     <li>同一材料对同一物品只能强化一次（已强化列表组件拦截）。</li>
 * </ul>
 * 输出数量 = 输入物品的整堆数量（动态生成，见 {@link #assemble}）；
 * 「整堆吞掉原物品」由 {@link com.plumejade.lensouls.reinforce.ReinforceCraftHandler}
 * 在工作台取出结果时清空原物品槽位实现（原版只会 removeItem(1)，无法整堆消耗）。
 */
public class ReinforceRecipe extends CustomRecipe {

    public ReinforceRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * 一次匹配结果。
     *
     * @param baseSlot     被强化物品所在槽位（{@link CraftingInput} 的索引）
     * @param materialSlot 强化材料所在槽位
     * @param base         被强化物品（原堆）
     * @param material     材料定义
     */
    public record Match(int baseSlot, int materialSlot, ItemStack base, ReinforceMaterial material) {
    }

    /**
     * 找出「恰好两个非空格子且构成 基物品 + 材料」的匹配；否则 null。
     * <p>
     * 两格都是材料（例如钻石 + 自然水晶）时，按槽位顺序尝试两种分配。
     */
    public static Match findMatch(CraftingInput input) {
        int first = -1;
        int second = -1;
        int nonEmpty = 0;
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).isEmpty()) continue;
            nonEmpty++;
            if (nonEmpty == 1) first = i;
            else if (nonEmpty == 2) second = i;
        }
        if (nonEmpty != 2) return null;
        Match match = tryAssign(input, first, second);
        return match != null ? match : tryAssign(input, second, first);
    }

    private static Match tryAssign(CraftingInput input, int baseSlot, int materialSlot) {
        ItemStack base = input.getItem(baseSlot);
        ResourceLocation materialId = ReinforceHelper.itemId(input.getItem(materialSlot));
        ReinforceMaterial material = ReinforceDataLoader.getMaterial(materialId);
        if (material == null) return null;
        if (!ReinforceHelper.canApply(base, materialId)) return null;
        return new Match(baseSlot, materialSlot, base, material);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    /**
     * 动态输出：完整副本 + 材料属性，数量取输入整堆。
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = findMatch(input);
        if (match == null) return ItemStack.EMPTY;
        ItemStack result = ReinforceHelper.createReinforced(match.base(), match.material().id());
        if (result.isEmpty()) return ItemStack.EMPTY;
        result.setCount(Math.max(1, match.base().getCount()));
        return result;
    }

    /** 材料按原版规则消耗 1 个；被强化物品整堆消耗在取出结果时处理，这里不返回任何剩余物。 */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ReinforceRecipes.REINFORCE.get();
    }
}
