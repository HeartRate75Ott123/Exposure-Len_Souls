package com.plumejade.lensouls.ability.gui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.plumejade.lensouls.LenSouls;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 能力界面的<b>逐帧</b>驱动钩子。
 *
 * <p>拖拽整理的手感全靠帧率：UI 自带的 {@code UIEvents.TICK} 是客户端 tick（20Hz），
 * 拿它做让位/收敛的缓动，动作会被切成 50ms 一台台阶，看着就是「平滑帧数不高」。
 * 这里挂在屏幕渲染前（每帧一次，与渲染同频）推进视觉状态。
 *
 * <p>只在 LDLib2 的容器界面（能力菜单就是它）打开时触发，其余界面零开销。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public final class AbilityGuiFrameTicker {

    private AbilityGuiFrameTicker() {
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof ModularUIContainerScreen)) return;
        AbilityGuiHolder.onFrame();
    }
}
