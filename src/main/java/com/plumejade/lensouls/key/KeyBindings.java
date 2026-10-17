package com.plumejade.lensouls.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.ClientBarrageState;
import com.plumejade.lensouls.client.ClientTamedPhotoSubject;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.gui.SoulSelectOverlay;
import com.plumejade.lensouls.item.ConverterItem;
import com.plumejade.lensouls.network.BarrageTogglePacket;
import com.plumejade.lensouls.network.ConverterMenuActivatePacket;
import com.plumejade.lensouls.network.ConverterMenuRequestPacket;
import com.plumejade.lensouls.network.ConverterModeSwitchPacket;
import com.plumejade.lensouls.network.ConverterTriggerPacket;
import com.plumejade.lensouls.network.PhotoOpenPacket;
import com.plumejade.lensouls.network.TamedPhotoSubjectTogglePacket;
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
 * 模式存于<b>转换器物品组件</b>（逐物品独立、随物品存档与同步），状态行也从组件读取。
 */
@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.lensouls";
    public static final String KEY_CONVERTER = "key.lensouls.converter";
    public static final String KEY_PHOTO_GUI = "key.lensouls.photo_gui";
    public static final String KEY_BARRAGE = "key.lensouls.barrage";
    /** 拍摄主体开关：驯服过的生物是否纳入相机拍摄主体选取 */
    public static final String KEY_TAMED_SUBJECT = "key.lensouls.tamed_subject";

    /** 触发模式常量落在公共类上（物品组件与 tooltip 共用），此处仅作别名 */
    public static final int MODE_FAST = ModDataComponents.CONVERTER_MODE_FAST;
    public static final int MODE_PRECISE = ModDataComponents.CONVERTER_MODE_PRECISE;

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

    /** 弹幕开关（默认 B）：切换是否触发照片弹幕，状态显示在物品栏上方 */
    private static final Lazy<net.minecraft.client.KeyMapping> BARRAGE_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_BARRAGE, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, KEY_CATEGORY));

    /**
     * 拍摄主体开关（默认 {@code [}）：切换「驯服过的生物是否纳入相机拍摄主体选取」，
     * 状态显示在物品栏上方（与弹幕开关同一套提示做法）。
     */
    private static final Lazy<net.minecraft.client.KeyMapping> TAMED_SUBJECT_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_TAMED_SUBJECT, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_BRACKET, KEY_CATEGORY));

    private static boolean converterKeyHeld = false;
    private static long pressStartNanos = 0;

    // ========== 触发模式 ==========

    /** 找玩家身上的转换器（主手 → 副手 → 背包），与 G 键触发时服务端的查找口径一致 */
    public static ItemStack findConverter(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack main = mc.player.getMainHandItem();
        if (main.getItem() instanceof ConverterItem) return main;
        ItemStack off = mc.player.getOffhandItem();
        if (off.getItem() instanceof ConverterItem) return off;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.getItem() instanceof ConverterItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 读取触发模式：以<b>转换器物品组件</b>为准（逐物品独立、随物品存档与同步）。
     * <p>
     * 物品上还没有组件时（旧存档首次使用）回退读取旧版玩家 persistentData 值，
     * 一旦切换过就会写入组件，此后一律以组件为准。
     */
    public static int getMode(Minecraft mc) {
        ItemStack converter = findConverter(mc);
        if (!converter.isEmpty() && ModDataComponents.hasConverterMode(converter)) {
            return ModDataComponents.getConverterMode(converter);
        }
        if (mc.player == null) return MODE_FAST;
        return mc.player.getPersistentData().getInt(ModDataComponents.LEGACY_MODE_TAG) == MODE_PRECISE
                ? MODE_PRECISE : MODE_FAST;
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONVERTER_KEY.get());
        event.register(PHOTO_GUI_KEY.get());
        event.register(BARRAGE_KEY.get());
        event.register(TAMED_SUBJECT_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (PHOTO_GUI_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new PhotoOpenPacket());
        }

        // 弹幕开关（B 键）：本地乐观翻转保证提示即时，再上报服务端翻转权威状态
        if (BARRAGE_KEY.get().consumeClick()) {
            boolean nowOn = ClientBarrageState.toggle();
            PacketDistributor.sendToServer(new BarrageTogglePacket());
            mc.player.displayClientMessage(barrageStatusMessage(nowOn), true);
        }

        // 拍摄主体开关（[ 键）：同上一套做法——本地乐观翻转，服务端权威裁决
        if (TAMED_SUBJECT_KEY.get().consumeClick()) {
            boolean nowIncluded = ClientTamedPhotoSubject.toggle();
            PacketDistributor.sendToServer(new TamedPhotoSubjectTogglePacket());
            mc.player.displayClientMessage(tamedSubjectStatusMessage(nowIncluded), true);
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
                    // 精准触发：请求服务端打开选择容器
                    PacketDistributor.sendToServer(new ConverterMenuRequestPacket());
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                converterKeyHeld = false;
                if (getMode(mc) == MODE_PRECISE && mc.screen instanceof SoulSelectOverlay overlay) {
                    int slot = overlay.getHoveredSlot();
                    if (slot >= 0 && overlay.isSlotReady(slot)) {
                        PacketDistributor.sendToServer(new ConverterMenuActivatePacket(slot));
                    }
                    mc.player.closeContainer();
                }
            }
        }
    }

    /** 精准模式：长按超阈值时确保容器菜单已打开（RequestPacket 异步到达，无需本地重建） */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (getMode(mc) != MODE_PRECISE) return;
        if (converterKeyHeld && !(mc.screen instanceof SoulSelectOverlay)
                && System.nanoTime() - pressStartNanos >= HOLD_THRESHOLD_NS) {
            // 按下时已发 RequestPacket，若尚未显示则重发（极端丢包/延迟场景）
            PacketDistributor.sendToServer(new ConverterMenuRequestPacket());
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
        // 模式写在物品组件上：本地乐观写入（保证紧接着的 G 键立刻用新模式），
        // 同时上报服务端权威写入并同步回来
        ModDataComponents.setConverterMode(held, mode);
        PacketDistributor.sendToServer(new ConverterModeSwitchPacket(mode));
        Component keyName = CONVERTER_KEY.get().getTranslatedKeyMessage();
        mc.player.displayClientMessage(
                Component.literal(mode == MODE_FAST
                        ? "§a触发模式：快速（按 "
                        : "§a触发模式：精准（长按 ")
                        .append(keyName)
                        // 键名之后补颜色码：§ 不跨 Component 继承，不补就成了白字（同弹幕/拍摄主体提示）
                        .append(mode == MODE_FAST ? "§a 直接触发）" : "§a 呼出菜单）"),
                true);
    }

    /**
     * 弹幕开关状态提示（显示在物品栏上方的动作栏）。
     * <p>
     * 措辞里嵌 {@code getTranslatedKeyMessage()}，所以玩家在「操作设置」里改了键之后，
     * 提示会跟着显示新键名，而不是永远写死一个「B」——与转换器模式的提示同一套做法。
     * <p>
     * <b>颜色码要补在键名之后</b>：{@code getTranslatedKeyMessage()} 返回的是<b>另一个</b>
     * {@code Component}，{@code §} 只作用在自己那一段文本里（那不是 style，不会向下继承），
     * 所以键名之后的「 切换）」必须自己再带一次颜色码，否则永远是白字——
     * 键名本身保持白色是刻意要的效果。
     */
    private static Component barrageStatusMessage(boolean enabled) {
        return Component.literal(enabled ? "§a弹幕：开启（按 " : "§c弹幕：关闭（按 ")
                .append(BARRAGE_KEY.get().getTranslatedKeyMessage())
                .append(enabled ? "§a 切换）" : "§c 切换）");
    }

    /**
     * 拍摄主体开关状态提示（物品栏上方的动作栏）。
     * <p>
     * 措辞同样嵌 {@code getTranslatedKeyMessage()}：玩家在「操作设置」里改键后提示会跟着变。
     * <p>
     * <b>颜色码要补在键名之后</b>：键名是<b>另一个</b> {@code Component}，{@code §} 只作用在自己那一段
     * 文本里（它不是 style、不向下继承），不补就成了白字「 切换）」——键名本身保持白色是刻意要的效果。
     */
    private static Component tamedSubjectStatusMessage(boolean included) {
        return Component.literal(included
                        ? "§a拍摄主体：含驯服生物（按 "
                        : "§c拍摄主体：不含驯服生物（按 ")
                .append(TAMED_SUBJECT_KEY.get().getTranslatedKeyMessage())
                .append(included ? "§a 切换）" : "§c 切换）");
    }

    private static boolean isConverterKeyEvent(InputEvent.Key event) {
        var key = CONVERTER_KEY.get().getKey();
        return key.getType() == InputConstants.Type.KEYSYM && event.getKey() == key.getValue();
    }
}
