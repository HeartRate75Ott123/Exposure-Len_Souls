package com.plumejade.lensouls.reinforce;

import com.plumejade.lensouls.recipe.ReinforceRecipe;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 工作台强化配方的「整堆消耗」实现。
 * <p>
 * 原版工作台取出结果时只对每个输入槽 {@code removeItem(1)}（{@code ResultSlot.onTake}），
 * 无法整堆消耗原物品。{@code ResultSlot.checkTakeAchievements} 会在消耗循环之前
 * 触发 {@link PlayerEvent.ItemCraftedEvent}，因此在这里把被强化物品槽位整堆清空：
 * <ul>
 *     <li>结果堆（数量 = 原堆数量）在事件之前就已经从结果槽取出交给玩家，不受影响；</li>
 *     <li>随后的消耗循环对已清空的槽位是空操作，只有材料槽会被 {@code removeItem(1)} 消耗 1 个；</li>
 *     <li>格子变化会重算结果槽，材料用尽 / 原物品清空后结果自然变空。</li>
 * </ul>
 * 注意：只有走原版 {@code ResultSlot} 的合成台会触发该事件；模组化自动合成器既不触发事件、
 * 也拿不到 {@link ReinforceRecipe#getIngredients()}（特殊配方无原料列表），因此不会绕过本逻辑。
 */
public class ReinforceCraftHandler {

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Container container = event.getInventory();
        if (!(container instanceof CraftingContainer grid)) return;
        if (grid.getContainerSize() < 2) return;

        CraftingInput input = grid.asCraftInput();
        ReinforceRecipe.Match match = ReinforceRecipe.findMatch(input);
        if (match == null) return;

        // 整堆移除原物品（材料槽留给原版的 removeItem(1) 消耗 1 个）
        ItemStack cleared = ItemStack.EMPTY;
        if (!grid.getItem(match.baseSlot()).isEmpty()) {
            grid.setItem(match.baseSlot(), cleared);
        }
    }
}
