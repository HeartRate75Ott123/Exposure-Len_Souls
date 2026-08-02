package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import com.plumejade.lensouls.item.CopySoulItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 元素羽毛禁复制合成：
 * 佩戴期间工作台输入含复制之魂时清空结果槽。
 * <p>
 * 注意：1.21.1 {@code CraftingMenu.slotsChanged} 调用
 * {@code slotChangedCraftingGrid} 时 recipeHolder 恒为 null（配方在方法内部重查），
 * 因此这里不依赖 recipeHolder 参数。
 * 扭曲值增加逻辑在 {@link com.plumejade.lensouls.mixin.ResultSlotMixin}（真实消耗时机）。
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void lensouls$blockCopySoulWithElementFeather(AbstractContainerMenu menu, Level level,
                                                                 Player player, CraftingContainer container,
                                                                 ResultContainer result,
                                                                 RecipeHolder<CraftingRecipe> recipeHolder,
                                                                 CallbackInfo ci) {
        if (level.isClientSide) return;

        if (!FeatherElementRiseHandler.hasFeather(player)) return;
        for (ItemStack stack : container.getItems()) {
            if (stack.getItem() instanceof CopySoulItem) {
                result.setItem(0, ItemStack.EMPTY);
                menu.setRemoteSlot(0, ItemStack.EMPTY);
                return;
            }
        }
    }
}
