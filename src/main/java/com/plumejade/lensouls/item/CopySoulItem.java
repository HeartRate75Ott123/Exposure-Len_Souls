package com.plumejade.lensouls.item;

import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.handler.FeatherAbyssHandler;
import com.plumejade.lensouls.handler.FeatherElementRiseHandler;
import com.plumejade.lensouls.handler.FeatherHardmanHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 复制之魂：工作台与任意物品合成，输出该物品的完整副本（附魔、组件、NBT 全保留）。
 * <p>
 * 1 复制之魂 + 任意物品 → 完整副本（数量等量）；合成逻辑见 {@link com.plumejade.lensouls.recipe.CopySoulRecipe}。
 * <p>
 * 禁复制判定（配方层，任何合成台统一生效）：
 * 佩戴禁复制羽毛（元素觉醒/铁人/深渊）期间，随身复制之魂在 {@link #inventoryTick} 被自动打上
 * 封印组件，配方（matches/assemble）见到封印即拒绝/输出空。
 * 封印绑定在物品栈上而非玩家判定——因为 {@code matches(CraftingInput, Level)} 拿不到合成者，
 * 栈级开关才能覆盖不走原版 CraftingMenu 的模组合成台与自动化。
 * 语义：羽毛气场封印随身之魂；放入箱子等容器后状态冻结（容器不 tick 物品），
 * 被无羽毛玩家捡起 1 tick 内自动解封。
 */
public class CopySoulItem extends Item {

    public CopySoulItem(Properties properties) {
        super(properties.stacksTo(64));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        boolean shouldSeal = FeatherElementRiseHandler.hasFeather(player)
                || FeatherHardmanHandler.hasHardman(player)
                || FeatherAbyssHandler.hasAbyss(player);
        boolean sealed = stack.has(ModDataComponents.COPY_SOUL_SEALED.get());
        if (shouldSeal == sealed) return; // 仅状态变化时写组件，避免每 tick 触发组件同步
        if (shouldSeal) {
            stack.set(ModDataComponents.COPY_SOUL_SEALED.get(), true);
        } else {
            stack.remove(ModDataComponents.COPY_SOUL_SEALED.get());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.lensouls.copy_soul.desc"));
    }
}
