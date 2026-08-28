package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.LenSouls;
import dev.xkmc.l2core.init.reg.simple.Reg;
import dev.xkmc.l2core.init.reg.simple.SR;
import dev.xkmc.l2core.init.reg.simple.Val;
import dev.xkmc.l2tabs.init.L2Tabs;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import dev.xkmc.l2tabs.tabs.inventory.InvTabData;
import net.minecraft.network.chat.Component;

/**
 * 把「照片效果」选项卡注册进 L2 Library 的 {@link L2Tabs#GROUP}（ABOVE 组）。
 * 注册后 l2tabs 会在玩家背包屏自动挂载该 tab，无需自写屏幕事件钩子。
 */
public class PhotoTabRegistry {

    public static final Reg REG = new Reg(LenSouls.MODID);
    public static final SR<TabToken<?, ?>> TAB_REG = SR.of(REG, L2Tabs.TABS.key());
    public static final Val<TabToken<InvTabData, PhotoEffectsTab>> TAB_PHOTO =
            TAB_REG.reg("photo_effects", () -> L2Tabs.GROUP.registerTab(() -> PhotoEffectsTab::new,
                    Component.translatable("lensouls.tabs.photo_effects")));

    public static void register() {
    }
}
