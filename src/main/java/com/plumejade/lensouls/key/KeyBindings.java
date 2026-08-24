package com.plumejade.lensouls.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.gui.SoulSelectOverlay;
import com.plumejade.lensouls.item.ConverterItem;
import com.plumejade.lensouls.network.ConverterMenuActivatePacket;
import com.plumejade.lensouls.network.ConverterMenuRequestPacket;
import com.plumejade.lensouls.network.ConverterTriggerPacket;
import com.plumejade.lensouls.network.PhotoOpenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 激活键（G）触发逻辑（参考 Sus-InstantSwap 状态机）：
 * <ul>
 *   <li>快速触发：按 G 直接按顺序尝试触发（ConverterTriggerPacket）</li>
 *   <li>精准触发：长按 G 呼出镜魂选择菜单，松开选择</li>
 * </ul>
 * 手持转换器左键空气切换触发模式；模式文字显示在物品栏上方。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.lensouls";
    public static final String KEY_CONVERTER = "key.lensouls.converter";
    public static final String KEY_PHOTO_GUI = "key.lensouls.photo_gui";

    public static final int MODE_FAST = 0;
    public static final int MODE_PRECISE = 1;
    private static final String MODE_TAG = "lensouls:converter_mode";

    /** 长按阈值（纳秒，300ms） */
    private static final long HOLD_THRESHOLD_NS = 300_000_000L;

    private static final Lazy<net.minecraft.client.KeyMapping> CONVERTER_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_CONVERTER, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));

    private static final Lazy<net.minecraft.client.KeyMapping> PHOTO_GUI_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_PHOTO_GUI, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, KEY_CATEGORY));

    private static boolean converterKeyHeld = false;
    private static long pressStartNanos = 0;

    // ========== 触发模式 ==========

    public static int getMode(Minecraft mc) {
        if (mc.player == null) return MODE_FAST;
        return mc.player.getPersistentData().getInt(MODE_TAG) == MODE_PRECISE ? MODE_PRECISE : MODE_FAST;
    }

    private static void setMode(Minecraft mc, int mode) {
        if (mc.player == null) return;
        mc.player.getPersistentData().putInt(MODE_TAG, mode);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONVERTER_KEY.get());
        event.register(PHOTO_GUI_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (PHOTO_GUI_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new PhotoOpenPacket());
        }

        // 激活键物理状态（Sus 式状态机）
        if (isConverterKeyEvent(event)) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                converterKeyHeld = true;
                pressStartNanos = System.nanoTime();
                if (getMode(mc) == MODE_FAST) {
                    // 快速触发：按下即按顺序尝试
                    PacketDistributor.sendToServer(new ConverterTriggerPacket());
                } else {
                    // 精准触发：请求转换器内容
                    PacketDistributor.sendToServer(new ConverterMenuRequestPacket());
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                converterKeyHeld = false;
                if (getMode(mc) == MODE_PRECISE && SoulSelectOverlay.isOpen()) {
                    if (SoulSelectOverlay.hasHovered()) {
                        PacketDistributor.sendToServer(
                                new ConverterMenuActivatePacket(SoulSelectOverlay.getHoveredSlot()));
                    }
                    SoulSelectOverlay.close(mc);
                }
            }
        }
    }

    /** 精准模式：长按超阈值 → 打开菜单（屏幕固定，不重建） */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (getMode(mc) != MODE_PRECISE) return;
        if (converterKeyHeld && !SoulSelectOverlay.isOpen()
                && System.nanoTime() - pressStartNanos >= HOLD_THRESHOLD_NS) {
            SoulSelectOverlay.open(mc);
        }
    }

    /** 手持转换器左键空气 → 切换触发模式（消息发送到物品栏上方） */
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!event.getEntity().level().isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!(held.getItem() instanceof ConverterItem)) return;

        int mode = getMode(mc) == MODE_FAST ? MODE_PRECISE : MODE_FAST;
        setMode(mc, mode);
        mc.player.displayClientMessage(
                Component.literal(mode == MODE_FAST
                        ? "§a触发模式：快速（按 G 直接触发）"
                        : "§a触发模式：精准（长按 G 呼出菜单）"),
                true);
    }

    private static boolean isConverterKeyEvent(InputEvent.Key event) {
        var key = CONVERTER_KEY.get().getKey();
        return key.getType() == InputConstants.Type.KEYSYM && event.getKey() == key.getValue();
    }
}
