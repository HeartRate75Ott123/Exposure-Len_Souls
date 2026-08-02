package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import com.plumejade.lensouls.item.CopySoulItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 佩戴羽·元素觉醒者的玩家无法使用复制之魂：
 * 工作台输入含复制之魂时清空结果槽（合成匹配完成后拦截）。
 * 佩戴期间无法进行任何复制合成。
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
    private static void lensouls$blockCopySoulWithFeather(AbstractContainerMenu menu, Level level, Player player,
                                                          CraftingContainer container, ResultContainer result,
                                                          RecipeHolder<CraftingRecipe> recipeHolder, CallbackInfo ci) {
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
