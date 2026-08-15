package com.plumejade.lensouls.ability.gui;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.ability.network.AbilitySelectPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 能力选择 HUD：屏幕右侧垂直居中的循环滚动列表。
 * <p>
 * 手持相机时常驻显示，最多 5 行（中心选中 + 上下各 2），中间项 100% 不透明度、
 * 向上下逐行衰减淡出；选中项有白色高亮横条（中心 30% → 两端 0% 渐变）。
 * 潜行 + 滚轮即时切换（C2S {@link AbilitySelectPacket}，服务端同步后校正），
 * 列表循环滚动，选择框锁定中间；未解锁任何能力时不渲染。
 * 滚动位置用浮点插值驱动，行位置与透明度随偏移连续变化，动效自然。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class AbilityWheelHud {

    private static final int ROW_WIDTH = 132;
    private static final int ROW_HEIGHT = 22;
    private static final int MARGIN_RIGHT = 8;
    /** 中心上下最多各几行（列表最多 2*WINDOW_MAX+1 = 5 行） */
    private static final int WINDOW_MAX = 2;
    /** 滚动插值系数（0~1，越大跟手越快，0.2 ≈ 9 tick 滑完一格） */
    private static final float SCROLL_SPEED = 0.2f;
    /** 每远离中心一行的透明度衰减比例 */
    private static final float ALPHA_FALLOFF = 0.35f;

    private static final String KEY_HINT = "gui.lensouls.ability.hint";

    /** 能力球物品图标缓存（按枚举序，懒加载：registry 就绪后首次渲染时填充） */
    private static final ItemStack[] ICONS = new ItemStack[AbilityType.values().length];

    /** 滚动目标（列表内索引，本地乐观更新，服务端 sync 后校正） */
    private static int target = 0;
    /** 平滑滚动位置（浮点，tick 步进驱动） */
    private static float scrollPos = 0f;
    /** 上一 tick 的滚动位置（渲染时在 prev→curr 之间补间，避免回跳振荡） */
    private static float prevScrollPos = 0f;

    // ========== 输入 ==========

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.options.keyShift.isDown()) return;
        if (!CameraInputHandler.isCamera(mc.player.getMainHandItem())) return;

        List<AbilityType> list = getUnlockedList();
        if (list.isEmpty()) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0) return;
        // 取消原版事件：阻止热栏滚动
        event.setCanceled(true);

        int size = list.size();
        // 与原版热栏一致：滚轮向下 → 下一个能力
        int dir = delta < 0 ? 1 : -1;
        target = Math.floorMod(target + dir, size);
        PacketDistributor.sendToServer(new AbilitySelectPacket(list.get(target).ordinal()));
    }

    // ========== 动画 ==========

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        List<AbilityType> list = getUnlockedList();
        if (list.isEmpty()) {
            target = 0;
            scrollPos = 0f;
            return;
        }
        int size = list.size();

        // 服务端 sync 校正（仅动画静止时生效：滚动进行中本地 target 权威，
        // 服务端回包延迟不再回拉；覆盖初始状态与拒绝等场景的兜底）
        if (Math.abs(scrollPos - target) < 0.001f) {
            AbilityType enabled = ClientAbilityCache.getEnabled();
            if (enabled != null) {
                int syncIdx = list.indexOf(enabled);
                if (syncIdx >= 0 && syncIdx != target) {
                    target = syncIdx;
                }
            }
        }

        // 归一化到 target 最近邻域（坐标系平移），prev 与 curr 同一坐标系，
        // 渲染补间只跨越 ≤1 格，环绕滚动（末↔头）平滑不横跳
        float prev = normalizeToward(scrollPos, target, size);
        prevScrollPos = prev;
        scrollPos = prev + (target - prev) * SCROLL_SPEED;
        if (Math.abs(scrollPos - target) < 0.001f) scrollPos = target;
    }

    // ========== 渲染 ==========

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!CameraInputHandler.isCamera(mc.player.getMainHandItem())) return;

        List<AbilityType> list = getUnlockedList();
        if (list.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int size = list.size();
        int x = g.guiWidth() - ROW_WIDTH - MARGIN_RIGHT;
        int centerY = g.guiHeight() / 2;

        // 窗口按实际能力数排版（中心对称，多余行放下方），最多 5 行：
        // 1行→[0]；2行→[0..1]；3行→[-1..1]；4行→[-1..2]；≥5行→[-2..2]
        int up = Math.min(WINDOW_MAX, (size - 1) / 2);
        int down = Math.min(WINDOW_MAX, size - 1 - up);

        // 提示文字（列表上方，&a 绿色）
        g.drawCenteredString(mc.font, Component.translatable(KEY_HINT),
                x + ROW_WIDTH / 2, centerY - up * ROW_HEIGHT - ROW_HEIGHT / 2 - 16, 0xFF55FF55);

        // 帧级补间：只把 tick 步进（prev→curr）平滑成连续动画，不插向 target，
        // partialTick 单调递增，永不回跳（修复回跳导致的抽搐与残影）
        float framePos = prevScrollPos + (scrollPos - prevScrollPos)
                * event.getPartialTick().getGameTimeDeltaPartialTick(false);

        // 平滑滚动：行位置由滚动偏移 slide 驱动（[-0.5, +0.5] 过渡），内容索引取整切换
        float rounded = (float) Math.floor(framePos + 0.5f);
        int centerIdx = Math.floorMod((int) rounded, size);
        float slide = rounded - framePos;

        for (int offset = -up; offset <= down; offset++) {
            int idx = Math.floorMod(centerIdx + offset, size);
            float rel = offset + slide;
            AbilityType type = list.get(idx);
            float alpha = Math.max(0f, 1f - ALPHA_FALLOFF * Math.abs(rel));
            int rowY = Math.round(centerY + rel * ROW_HEIGHT - ROW_HEIGHT / 2f);

            // 选中项高亮：白色横条，水平渐变 30%（左）→ 0%（右）
            if (Math.abs(rel) <= 0.5f) {
                drawHighlight(g, x, rowY);
            }

            // 能力球图标（物品渲染，透明度随行衰减）
            g.setColor(1f, 1f, 1f, alpha);
            g.renderItem(getIcon(type), x + 5, rowY + 3);
            g.setColor(1f, 1f, 1f, 1f);

            // 中文能力名（透明度随行衰减）
            int textColor = (((int) (alpha * 255)) << 24) | 0xFFFFFF;
            g.drawString(mc.font, Component.translatable(type.getNameKey()),
                    x + 27, rowY + (ROW_HEIGHT - 9) / 2, textColor);
        }
    }

    /** 白色高亮横条：3px 细段近似水平渐变，左端 30% 不透明度线性衰减到右端 0（视觉连续无台阶） */
    private static void drawHighlight(GuiGraphics g, int x, int rowY) {
        int segW = 3;
        int top = rowY - 2;
        int height = ROW_HEIGHT + 4;
        int width = ROW_WIDTH + 6;
        for (int i = 0; i * segW < width; i++) {
            int segLeft = x - 3 + i * segW;
            // 按段中心插值透明度，线性 30% → 0
            float t = 1f - (segLeft + segW / 2f - (x - 3)) / (float) width;
            int alpha = Math.round(0.30f * 255 * t);
            if (alpha <= 0) break;
            int segRight = Math.min(segLeft + segW, x - 3 + width);
            g.fill(segLeft, top, segRight, top + height, (alpha << 24) | 0xFFFFFF);
        }
    }

    /** 懒加载能力球图标（registry 就绪后首次渲染时填充） */
    private static ItemStack getIcon(AbilityType type) {
        int ordinal = type.ordinal();
        if (ICONS[ordinal] == null) {
            ICONS[ordinal] = new ItemStack(BuiltInRegistries.ITEM.get(type.getIconItemId()));
        }
        return ICONS[ordinal];
    }

    // ========== 数据 ==========

    /**
     * 把 value 归一化到 target 的最近邻域（循环取模语义）：
     * 返回的 value' 满足 |value' - target| ≤ size/2，且 value' ≡ value (mod size)。
     */
    private static float normalizeToward(float value, float target, int size) {
        float v = value;
        while (v - target > size / 2f) v -= size;
        while (target - v > size / 2f) v += size;
        return v;
    }

    /** 已解锁能力列表（新解锁最前，与容器 GUI 排序一致） */
    private static List<AbilityType> getUnlockedList() {
        List<AbilityType> result = new ArrayList<>();
        List<AbilityType> order = ClientAbilityCache.getUnlockOrder();
        for (int i = order.size() - 1; i >= 0; i--) {
            AbilityType type = order.get(i);
            if (ClientAbilityCache.isUnlocked(type) && !result.contains(type)) result.add(type);
        }
        for (AbilityType type : AbilityType.values()) {
            if (ClientAbilityCache.isUnlocked(type) && !result.contains(type)) result.add(type);
        }
        return result;
    }
}