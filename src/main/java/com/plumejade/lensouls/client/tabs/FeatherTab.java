package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.network.FeatherOpenPacket;
import dev.xkmc.l2tabs.tabs.core.TabBase;
import dev.xkmc.l2tabs.tabs.core.TabManager;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import dev.xkmc.l2tabs.tabs.inventory.InvTabData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「羽毛装配」选项卡：点击后请求服务端打开 {@code FeatherSlotMenu}（客户端收到开屏包即显示全屏界面）。
 * <p>
 * 图标用自绘纹理 {@code textures/gui/feather_tab_icon.png}（需求指定的 icon 资产），
 * 不走 l2tabs 的物品图标入口。
 */
public class FeatherTab extends TabBase<InvTabData, FeatherTab> {

    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath("lensouls", "textures/gui/feather_tab_icon.png");
    private static final int ICON_SIZE = 16;
    /** ABOVE 组的图标偏移（与 TabType.drawIcon 的 per-type 偏移一致） */
    private static final int ICON_OFFSET_X = 6;
    private static final int ICON_OFFSET_Y = 9;

    public FeatherTab(int index, TabToken<InvTabData, FeatherTab> token,
                      TabManager<InvTabData> manager, Component title) {
        super(index, token, manager, title);
    }

    @Override
    public void onTabClicked() {
        // 记住点分页时的光标位置：服务端开屏后由 FeatherScreen.init() 复原（否则会被刷到屏幕中央）
        com.plumejade.lensouls.client.feather.CursorKeeper.remember();
        // 菜单必须由服务端打开（槽内容 + 锁定位的同步前提）
        PacketDistributor.sendToServer(new FeatherOpenPacket());
    }

    @Override
    protected void renderIcon(GuiGraphics g) {
        // ★ 必须带框架的图标偏移，否则贴图不居中：TabType.drawIcon 按组偏移
        //   （ABOVE +6/+9，BELOW +6/+6，LEFT +10/+5，RIGHT +6/+5；我们的分页在 ABOVE 组）。
        //   直接 blit 在 getX()/getY() 会明显偏左上——实测踩过。
        g.blit(ICON, getX() + ICON_OFFSET_X, getY() + ICON_OFFSET_Y,
                0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }
}
