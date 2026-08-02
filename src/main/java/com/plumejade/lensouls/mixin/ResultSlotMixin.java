package com.plumejade.lensouls.mixin;

import com.plumejade.lensouls.handler.FeatherTwitcherHandler;
import com.plumejade.lensouls.item.CopySoulItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 扭曲羽毛扭曲值：在 {@link ResultSlot#onTake}（结果真正被取走的瞬间）
 * 统计输入容器中含复制之魂的槽位数（每槽消耗 1 个），按实际消耗数量增加扭曲值。
 * <p>
 * onTake 在 removeItem 消耗之前调用，输入容器内容完整；
 * 该注入点天然只在真实合成发生时触发一次，不会因格子预览重复计数。
 * CraftingMenu（3×3）与 InventoryMenu（2×2）共用 ResultSlot，一并覆盖。
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("lensouls.crafting");

    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Shadow
    @Final
    private Player player;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void lensouls$addTwistOnSoulConsumed(Player ignored, ItemStack ignoredStack, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        if (!FeatherTwitcherHandler.hasTwitcher(player)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        int soulSlots = 0;
        for (ItemStack stack : this.craftSlots.getItems()) {
            if (stack.getItem() instanceof CopySoulItem) {
                soulSlots++;
            }
        }
        if (soulSlots > 0) {
            LOGGER.info("[CraftMixin] {} crafted, consuming {} copy soul slot(s)", player.getName().getString(), soulSlots);
            FeatherTwitcherHandler.addTwist(serverPlayer, soulSlots);
        }
    }
}
