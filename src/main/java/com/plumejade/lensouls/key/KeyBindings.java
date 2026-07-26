package com.plumejade.lensouls.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.GoldGlintRenderTypes;
import com.plumejade.lensouls.ability.client.GoldenPlayerGlintLayer;
import com.plumejade.lensouls.network.ConverterTriggerPacket;
import com.plumejade.lensouls.network.PhotoOpenPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = LenSouls.MODID, value = Dist.CLIENT)
public class KeyBindings {

    public static final String KEY_CATEGORY = "key.category.lensouls";
    public static final String KEY_CONVERTER = "key.lensouls.converter";
    public static final String KEY_PHOTO_GUI = "key.lensouls.photo_gui";
    public static final String KEY_GOLD_GLINT = "key.lensouls.gold_glint";

    private static final Lazy<net.minecraft.client.KeyMapping> CONVERTER_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_CONVERTER,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_G,
                    KEY_CATEGORY
            ));

    private static final Lazy<net.minecraft.client.KeyMapping> PHOTO_GUI_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_PHOTO_GUI,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_X,
                    KEY_CATEGORY
            ));

    /** 调试：切换玩家金色 glint 光效 */
    private static final Lazy<net.minecraft.client.KeyMapping> GOLD_GLINT_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_GOLD_GLINT,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_K,
                    KEY_CATEGORY
            ));

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONVERTER_KEY.get());
        event.register(PHOTO_GUI_KEY.get());
        event.register(GOLD_GLINT_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (CONVERTER_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new ConverterTriggerPacket());
        }
        if (PHOTO_GUI_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new PhotoOpenPacket());
        }
        if (GOLD_GLINT_KEY.get().consumeClick()) {
            boolean on = !GoldGlintRenderTypes.enabled;
            GoldGlintRenderTypes.enabled = on;
            GoldenPlayerGlintLayer.visible = on;
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§6金色光效: " + (on ? "§a§l开启" : "§c§l关闭")),
                        true);
            }
        }
    }
}
