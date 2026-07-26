package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.item.DimensionalGunItem;
import com.plumejade.lensouls.item.GravityGunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 修改第一人称手臂拉弓动画的蓄力进度除数 20.0F，使其跟随武器实际蓄力时长。
 * <p>
 * morearrows 模组已经用同款手法修改了 BOW 的除数，但这个
 * mixin 的 {@code getUseItem().is(Items.BOW)} 检查会跳过我们的枪。
 * 我们再加一层处理：如果当前使用物品是 lensouls 的枪，
 * 返回对应的 chargeTicks 作为除数，使拉弓动画速度与实际蓄力时间对齐。
 * </p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class GunBowAnimationMixin {

    @ModifyConstant(
        method = "renderArmWithItem",
        constant = @Constant(floatValue = 20.0F),
        require = 0
    )
    private float lensouls$modifyGunBowDivisor(float original) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem()) return original;
        ItemStack stack = player.getUseItem();

        // 次元枪：用动态蓄力时长作为除数
        if (stack.getItem() instanceof DimensionalGunItem gun) {
            return Math.max(gun.getChargeTicks(stack), 1);
        }
        // 引力枪：0.3s = 6 ticks
        if (stack.getItem() instanceof GravityGunItem) {
            return 6;
        }
        return original;
    }
}
