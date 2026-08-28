package com.plumejade.lensouls.client.itemoutline;

import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 第一人称手持物描边触发判定。
 * <p>
 * 仅在第一人称手持物上下文、且玩家当前持有活跃元素配色（镜魂元素附魔）时返回单色描边数据；
 * 其他所有渲染（GUI / 地面 / 第三人称）一律不触发，沿用原版或其他描边系统。
 */
public class ItemOutlineDispatcher {

    /** 第一人称手持物描边粗细（像素）。改这里即可调粗细；需保证 shader 中 maxR ≥ 此值 */
    private static final int FIRST_PERSON_OUTLINE_WIDTH = 6;

    public static ItemOutlineData getOutline(ItemStack stack, ItemDisplayContext context) {
        if (stack == null || stack.isEmpty()) return null;
        if (context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                && context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui) return null;
        LocalPlayer player = mc.player;
        if (player == null) return null;
        BossOutlineColors colors = BossOutlineColors.fromEntity(player);
        if (colors == null) return null;
        int rgb = colors.primaryColor();
        if (rgb == 0) return null;
        // 第一人称手持物描边粗细：单一像素常量，独立于 BOSS 描边的 outlineWidth
        int radius = FIRST_PERSON_OUTLINE_WIDTH;
        return new ItemOutlineData(rgb, radius);
    }
}
