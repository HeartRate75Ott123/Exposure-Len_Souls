package com.plumejade.lensouls.ability.gui;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.ability.client.ClientAbilityCache;
import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.ability.network.AbilitySelectPacket;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.camera.CameraId;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
    /** 满窗口（≥5 能力）时边缘行保留的透明度（不足 5 个时边缘淡出到底，见 onRenderGui） */
    private static final float EDGE_ALPHA_FULL = 0.30f;

    private static final String KEY_HINT = "gui.lensouls.ability.hint";
    private static final String KEY_HINT_OPEN = "gui.lensouls.ability.hint_open";

    /** 能力球物品图标缓存（按枚举序，懒加载：registry 就绪后首次渲染时填充） */
    private static final ItemStack[] ICONS = new ItemStack[AbilityType.values().length];

    /** 滚动目标（物理单调坐标，可超出 [0,size)，跨环不翻转方向；仅发包/渲染时取模） */
    private static int physicalTarget = 0;
    /** 平滑滚动位置（浮点，tick 步进驱动） */
    private static float scrollPos = 0f;
    /** 上一 tick 的滚动位置（渲染时在 prev→curr 之间补间，避免回跳振荡） */
    private static float prevScrollPos = 0f;
    /** 上一帧手持相机的 camera_id（用于换机时本地播种选中镜像，无网络延迟） */
    private static java.util.UUID lastHeldCameraId = null;

    // ========== 输入 ==========

    @SubscribeEvent
    public static void onMouseScrolled(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!mc.options.keyShift.isDown()) return;
        if (CameraInputHandler.getWieldedCamera(mc.player).isEmpty()) return;

        double delta = event.getScrollDeltaY();
        if (delta == 0) return;

        List<AbilityType> list = getUnlockedList();
        // 仅一个能力时滚动无效，保持固定
        if (list.size() <= 1) return;
        // 取消原版事件：阻止热栏滚动
        event.setCanceled(true);

        int size = list.size();
        // 与原版热栏一致：滚轮向下 → 下一个能力
        int dir = delta < 0 ? 1 : -1;
        // 物理坐标单调累加，不取模：跨环（0↔size-1）方向保持连续，动画不翻转
        physicalTarget += dir;
        PacketDistributor.sendToServer(
                new AbilitySelectPacket(list.get(Math.floorMod(physicalTarget, size)).ordinal()));
    }

    // ========== 生命周期 ==========

    /**
     * 客户端登出（退到标题/切档）时重置手持相机标记。
     * 否则同 JVM 内重进同一存档时 CAMERA_ID 不变，会跳过 onClientTick 的换机播种，
     * 导致 ClientAbilityCache.currentEnabled 停留在登出时的 null，HUD 误显「未选中」。
     */
    @SubscribeEvent
    public static void onClientLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastHeldCameraId = null;
    }

    // ========== 动画 ==========

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 换机播种：手持相机变更时本地读取其选中项，即时更新镜像（不依赖滞后 NBT 同步）
        ItemStack held = CameraInputHandler.getWieldedCamera(mc.player);
        if (!held.isEmpty()) {
            CameraId cid = held.get(Exposure.DataComponents.CAMERA_ID);
            java.util.UUID id = cid != null ? cid.uuid() : null;
            if (id == null || !id.equals(lastHeldCameraId)) {
                lastHeldCameraId = id;
                AbilityType sel = CameraAbilityStore.getSelectedType(held);
                ClientAbilityCache.setHeldCameraSelected(sel != null ? sel.ordinal() : -1);
            }
        } else {
            lastHeldCameraId = null;
        }

        List<AbilityType> list = getUnlockedList();
        if (list.isEmpty()) {
            physicalTarget = 0;
            scrollPos = 0f;
            prevScrollPos = 0f;
            return;
        }
        int size = list.size();

        // 服务端 sync 校正（仅动画静止时生效：滚动进行中本地物理坐标权威，
        // 服务端回包延迟不再回拉）。校正保持物理连续：把当前取模项对齐到 sync 项。
        if (Math.abs(scrollPos - physicalTarget) < 0.001f) {
            AbilityType enabled = ClientAbilityCache.getEnabled();
            if (enabled != null) {
                int syncIdx = list.indexOf(enabled);
                if (syncIdx >= 0) {
                    int currentIdx = Math.floorMod(Math.round(scrollPos), size);
                    if (currentIdx != syncIdx) {
                        physicalTarget = Math.round(scrollPos) - currentIdx + syncIdx;
                        scrollPos = physicalTarget;
                        prevScrollPos = scrollPos;
                    }
                }
            }
        }

        // 物理坐标单调逼近（不取模、不归一化）：跨环动画沿滚动方向走 1 格，
        // 方向永不翻转，prev 与 curr 同一坐标系，渲染补间无横跳
        prevScrollPos = scrollPos;
        scrollPos += (physicalTarget - scrollPos) * SCROLL_SPEED;
        if (Math.abs(scrollPos - physicalTarget) < 0.001f) scrollPos = physicalTarget;
    }

    // ========== 渲染 ==========

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (CameraInputHandler.getWieldedCamera(mc.player).isEmpty()) return;

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

        // 提示文字（列表上方，&a 绿色）：第一行=左键打开界面，第二行=潜行滚轮切换
        int hintTop = centerY - up * ROW_HEIGHT - ROW_HEIGHT / 2 - 27;
        g.drawCenteredString(mc.font, Component.translatable(KEY_HINT_OPEN),
                x + ROW_WIDTH / 2, hintTop, 0xFF55FF55);
        g.drawCenteredString(mc.font, Component.translatable(KEY_HINT),
                x + ROW_WIDTH / 2, hintTop + 11, 0xFF55FF55);

        // 帧级补间：只把 tick 步进（prev→curr）平滑成连续动画，不插向 target，
        // partialTick 单调递增，永不回跳（修复回跳导致的抽搐与残影）
        float framePos = prevScrollPos + (scrollPos - prevScrollPos)
                * event.getPartialTick().getGameTimeDeltaPartialTick(false);

        // 平滑滚动：行位置由滚动偏移 slide 驱动（[-0.5, +0.5] 过渡），内容索引取整切换
        float rounded = (float) Math.floor(framePos + 0.5f);
        int centerIdx = Math.floorMod((int) rounded, size);
        float slide = rounded - framePos;

        // 渲染范围含上下各 1 格缓冲行：滑入/滑出的行全程渲染，
        // 透明度由 rowAlpha 连续曲线给出，窗口边界处无跳变、无残留消失
        for (int offset = -up - 1; offset <= down + 1; offset++) {
            int idx = Math.floorMod(centerIdx + offset, size);
            float rel = offset + slide;
            AbilityType type = list.get(idx);
            float alpha = rowAlpha(rel, up, down);
            int rowY = Math.round(centerY + rel * ROW_HEIGHT - ROW_HEIGHT / 2f);

            // 选中项高亮：白色横条，水平渐变 30%（左）→ 0%（右）。
            // 仅在手持相机确有选中能力时绘制——取消选择后白框不渲染，潜行滚动选定后自动浮现。
            if (Math.abs(rel) <= 0.5f && ClientAbilityCache.getEnabled() != null) {
                drawHighlight(g, x, rowY);
            }

            // 图标与文字用同一 setColor 控制透明度（颜色 alpha 位固定不透明），
            // 两者必然同步渐隐——避免 drawString 颜色 alpha 位失效导致文字不透明
            g.setColor(1f, 1f, 1f, alpha);
            g.renderItem(getIcon(type), x + 5, rowY + 3);
            g.drawString(mc.font, Component.translatable(type.getNameKey()),
                    x + 27, rowY + (ROW_HEIGHT - 9) / 2, 0xFFFFFFFF);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /**
     * 行的渐隐透明度（只依赖 |rel|，与槽位无关，跨槽位切换连续无跳变）：
     * 窗口内：中心 100% → 边缘行中心 EDGE_ALPHA_FULL(30%)（up/down 各自斜率）；
     * 窗口外缓冲段：30% → 出窗口点（+0.5 格）线性归零；
     * 该方向无窗口行（up/down = 0，如 2 个能力的上方）：缓冲行从出窗口点 0% 渐显到中心 100%。
     */
    private static float rowAlpha(float rel, int up, int down) {
        float depth = rel < 0 ? up : down;
        float absRel = Math.abs(rel);
        if (depth <= 0) {
            // 无窗口行的方向：缓冲行只在滚动时短暂经过，从出窗口点(0.5格)渐显到中心
            return Math.max(0f, 1f - 2f * absRel);
        }
        float inner = Math.min(absRel, depth);
        float beyond = Math.max(0f, absRel - depth);
        // 窗口内斜率 (1-30%)/depth，窗口外斜率 30%/0.5格
        float alpha = 1f - (1f - EDGE_ALPHA_FULL) / depth * inner
                - EDGE_ALPHA_FULL / 0.5f * beyond;
        return Math.max(0f, alpha);
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