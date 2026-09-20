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
 * <b>输出数量恒为 1</b>（见 {@link #assemble}）：配方契约是「输入按每槽 1 个消耗、本方法给出这一份的产出」，
 * 原版工作台、便携工作台（复用原版 {@code CraftingMenu}）、自动合成器与各模组自研合成逻辑都遵循它。
 * 输出多于消耗就是复制，所以所有合成台一律「1 个材料 = 1 次强化」。
 * <p>
 * 「1 个材料 = 整堆强化」只由次元锤界面（{@code ReinforceMenu}）提供：它直接改槽位、由模组自己扣料，
 * 不走合成配方，因此不受该契约限制。
 * 材料由 {@link #getRemainingItems} 返回全空，因此原版循环恰好消耗 1 个。
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
     * 输出：完整副本 + 材料属性，<b>数量固定 1</b>。
     * <p>
     * <b>为什么不能输出整堆</b>：配方契约是「{@code assemble} 给出这一份配方的产出，输入按每槽 1 个消耗」。
     * 原版工作台与模组合成台（便携工作台、精妙背包合成升级等）都按这个契约处理消耗——它们只扣 1 个原物品，
     * 却会把 {@code assemble} 的数量整份交给玩家。若这里返回「原堆数量」，就等于「1 个原料换一整堆强化物」，
     * 在这些站台上会变成恶性的物品大量复制（实测踩过）。整堆能力<b>只能</b>放在我们自己可控的取走路径上
     * （见 {@link com.plumejade.lensouls.mixin.ReinforceCraftConsumeMixin}，仅原版 {@code ResultSlot} 的快速移动分支）。
     */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Match match = findMatch(input);
        if (match == null) return ItemStack.EMPTY;
        ItemStack result = ReinforceHelper.createReinforced(match.base(), match.material().id());
        if (result.isEmpty()) return ItemStack.EMPTY;
        result.setCount(1);
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
