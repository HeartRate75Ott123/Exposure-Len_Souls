package com.plumejade.lensouls.integration;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

public class CuriosIntegration {

    private static boolean registered = false;
    private static Object TRI_STATE_TRUE;
    private static Object TRI_STATE_DENY;

    public static void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventClass = Class.forName("top.theillusivec4.curios.api.event.CurioCanEquipEvent");
            Method getStack = eventClass.getMethod("getStack");
            Method getSlotContext = eventClass.getMethod("getSlotContext");
            Method setEquipResult = resolveSetEquipResult(eventClass);
            Method getIdentifier = Class.forName("top.theillusivec4.curios.api.SlotContext").getMethod("identifier");

            registerListener(eventClass, EventPriority.NORMAL, (Consumer<Object>) event -> {
                try {
                    Object ctx = getSlotContext.invoke(event);
                    String slotId = (String) getIdentifier.invoke(ctx);
                    if (!"photograph".equals(slotId) && !"lensouls:photograph".equals(slotId)) return;
                    net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) getStack.invoke(event);
                    boolean hasTag = false;
                    if (stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) != null) {
                        hasTag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                                .copyTag().getBoolean("lensouls:photograph_curio");
                    }
                    setEquipResult.invoke(event, hasTag ? TRI_STATE_TRUE : TRI_STATE_DENY);
                } catch (Exception e) {
                    com.plumejade.lensouls.LenSouls.LOGGER.error("[Curios] 验证失败", e);
                }
            });

            com.plumejade.lensouls.LenSouls.LOGGER.info("[Curios] 照片饰品槽已注册");
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.info("[Curios] Curios 未安装");
        }
    }

    private static void registerListener(Class<?> eventClass, EventPriority priority, Consumer<Object> handler) throws Exception {
        Method addListener = NeoForge.EVENT_BUS.getClass()
                .getMethod("addListener", EventPriority.class, boolean.class, Class.class, Consumer.class);
        addListener.invoke(NeoForge.EVENT_BUS, priority, false, eventClass, handler);
    }

    private static Method resolveSetEquipResult(Class<?> eventClass) throws Exception {
        for (var m : eventClass.getMethods()) {
            if ("setEquipResult".equals(m.getName()) && m.getParameterCount() == 1) {
                Class<?> triStateClass = m.getParameterTypes()[0];
                for (var f : triStateClass.getFields()) {
                    if ("TRUE".equals(f.getName())) TRI_STATE_TRUE = f.get(null);
                    if ("DENY".equals(f.getName())) TRI_STATE_DENY = f.get(null);
                }
                return m;
            }
        }
        throw new NoSuchMethodException("setEquipResult");
    }
}
