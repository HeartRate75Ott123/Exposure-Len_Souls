package com.plumejade.lensouls.client.handler;

import com.plumejade.lensouls.item.DimensionalGunItem;
import com.plumejade.lensouls.network.DimensionalGunCyclePacket;
import com.plumejade.lensouls.network.DimensionalGunTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(value = Dist.CLIENT)
public class GunInputHandler {

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof DimensionalGunItem)) return;

        // Only on air (no entity targeted)
        if (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) {
            long window = mc.getWindow().getWindow();
            boolean shiftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            if (shiftDown) {
                PacketDistributor.sendToServer(new DimensionalGunTogglePacket());
            } else {
                PacketDistributor.sendToServer(new DimensionalGunCyclePacket());
            }
        }
    }
}
