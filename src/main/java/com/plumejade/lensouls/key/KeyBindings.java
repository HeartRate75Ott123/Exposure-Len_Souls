package com.plumejade.lensouls.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.network.ConverterTriggerPacket;
import com.plumejade.lensouls.network.AbilityListPacket;
import com.plumejade.lensouls.network.PhotoOpenPacket;
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
    public static final String KEY_ABILITY_LIST = "key.lensouls.ability_list";

    private static final Lazy<net.minecraft.client.KeyMapping> CONVERTER_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_CONVERTER, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KEY_CATEGORY));

    private static final Lazy<net.minecraft.client.KeyMapping> PHOTO_GUI_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_PHOTO_GUI, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, KEY_CATEGORY));

    private static final Lazy<net.minecraft.client.KeyMapping> ABILITY_LIST_KEY =
            Lazy.of(() -> new net.minecraft.client.KeyMapping(
                    KEY_ABILITY_LIST, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_SEMICOLON, KEY_CATEGORY));

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONVERTER_KEY.get());
        event.register(PHOTO_GUI_KEY.get());
        event.register(ABILITY_LIST_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (CONVERTER_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new ConverterTriggerPacket());
        }
        if (PHOTO_GUI_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new PhotoOpenPacket());
        }
        if (ABILITY_LIST_KEY.get().consumeClick()) {
            PacketDistributor.sendToServer(new AbilityListPacket());
        }
    }
}
