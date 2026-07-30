package com.plumejade.lensouls.integration;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public class CuriosIntegration {

    @SubscribeEvent
    public static void onCurioCanEquip(CurioCanEquipEvent event) {
        String slotId = event.getSlotContext().identifier();
        if (!"photograph".equals(slotId) && !"lensouls:photograph".equals(slotId)) return;

        ItemStack stack = event.getStack();
        boolean hasTag = false;
        if (stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA) != null) {
            hasTag = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                    .copyTag().getBoolean("lensouls:photograph_curio");
        }
        event.setEquipResult(hasTag ? TriState.TRUE : TriState.FALSE);
    }
}
