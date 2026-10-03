package com.plumejade.lensouls.client.tabs;

import dev.xkmc.l2core.init.reg.simple.SR;
import dev.xkmc.l2core.init.reg.simple.Val;
import dev.xkmc.l2tabs.init.L2Tabs;
import dev.xkmc.l2tabs.tabs.core.TabToken;
import dev.xkmc.l2tabs.tabs.inventory.InvTabData;
import net.minecraft.network.chat.Component;

/**
 * 把「羽毛装配」选项卡注册进 L2 Library 的 {@link L2Tabs#GROUP}（与照片效果同一组）。
 * <p>
 * 复用 {@link PhotoTabRegistry#REG}：同一个模组只应有一个 {@code Reg} 实例，
 * 另建一个会让 l2core 的注册名冲突。
 */
public class FeatherTabRegistry {

    public static final SR<TabToken<?, ?>> TAB_REG = SR.of(PhotoTabRegistry.REG, L2Tabs.TABS.key());

    public static final Val<TabToken<InvTabData, FeatherTab>> TAB_FEATHER =
            TAB_REG.reg("feather_slots", () -> L2Tabs.GROUP.registerTab(() -> FeatherTab::new,
                    Component.translatable("lensouls.tabs.feather")));

    public static void register() {
    }
}
