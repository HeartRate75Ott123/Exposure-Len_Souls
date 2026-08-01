package com.plumejade.lensouls.integration;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.Random;

import java.util.Random;

/**
 * 暮色头颅饰品修饰符处理器。
 * 玩家首次装备头颅时随机获得一项韧性修饰符，持久化到物品。
 */
public class TrophyModifierHandler {

    private static final String TAG = "lensouls:trophy_mod";
    private static final String[] MODS = {"toughness_bonus", "stun_extend", "invincible_shorten"};
    private static final ResourceLocation HEAD_SLOT = ResourceLocation.parse("head");

    /** 获取头颅上的修饰符 */
    public static TrophyMod getModifier(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        if (!tag.contains(TAG)) return null;
        var mt = tag.getCompound(TAG);
        String type = mt.getString("type");
        float value = mt.getFloat("value");
        if (type.isEmpty()) return null;
        return new TrophyMod(type, value);
    }

    /** 首次获取时生成随机修饰符 */
    private static TrophyMod rollModifier() {
        Random rnd = new Random();
        String type = MODS[rnd.nextInt(MODS.length)];
        float value = switch (type) {
            case "toughness_bonus" -> 1.0f + rnd.nextInt(3);             // +1 ~ +3 次削韧（随机，加法叠加）
            case "stun_extend" -> 1.25f + rnd.nextInt(6) * 0.05f;    // +25% ~ +50% 步长5%
            case "invincible_shorten" -> 0.50f + rnd.nextInt(6) * 0.05f; // ×50% ~ ×75% 步长5%
            default -> 1.0f;
        };
        return new TrophyMod(type, value);
    }

    /** 每 tick 检查：首次装备时生成修饰符 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var stackHandler = stacksHandler.getStacks();
                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    String regName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (!regName.contains("twilightforest") || !regName.contains("trophy")) continue;
                    if (getModifier(stack) != null) continue;
                    TrophyMod mod = rollModifier();
                    CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    CompoundTag mt = new CompoundTag();
                    mt.putString("type", mod.type);
                    mt.putFloat("value", mod.value);
                    tag.put(TAG, mt);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                    try { stacksHandler.getStacks().setStackInSlot(i, stack); } catch (Exception ignored) {}
                }
            }
        });
    }

    public record TrophyMod(String type, float value) {}

    private static java.util.List<ItemStack> getTrophyStacks(ServerPlayer player) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var sh : handler.getCurios().values()) {
                var stacks = sh.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack stack = stacks.getStackInSlot(i);
                    if (!stack.isEmpty() && getModifier(stack) != null) list.add(stack);
                }
            }
        });
        return list;
    }

    public static float applyHitsModifier(ServerPlayer player, float baseHits) {
        float total = baseHits;
        for (ItemStack stack : getTrophyStacks(player)) {
            TrophyMod m = getModifier(stack);
            if (m != null && "toughness_bonus".equals(m.type)) total += m.value;
        }
        return total;
    }

    public static int applyStunModifier(ServerPlayer player, int baseStun) {
        float sumBonus = 0f;
        for (ItemStack stack : getTrophyStacks(player)) {
            TrophyMod m = getModifier(stack);
            if (m != null && "stun_extend".equals(m.type)) sumBonus += (m.value - 1f);
        }
        return (int) (baseStun * (1f + sumBonus));
    }

    public static int applyInvincibleModifier(ServerPlayer player, int baseInvincible) {
        float sumBonus = 0f;
        for (ItemStack stack : getTrophyStacks(player)) {
            TrophyMod m = getModifier(stack);
            if (m != null && "invincible_shorten".equals(m.type)) sumBonus += (m.value - 1f);
        }
        return Math.max(1, (int) (baseInvincible * (1f + sumBonus)));
    }

    @SubscribeEvent
    public static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        TrophyMod m = getModifier(stack);
        if (m == null) return;
        String line = switch (m.type) {
            case "toughness_bonus" -> "§6韧性伤害 +" + String.format("%.0f", m.value);
            case "stun_extend" -> "§6破刹时长 ×" + String.format("%.0f%%", m.value * 100);
            case "invincible_shorten" -> "§6白霸体时长 ×" + String.format("%.0f%%", m.value * 100);
            default -> "";
        };
        if (!line.isEmpty()) event.getToolTip().add(net.minecraft.network.chat.Component.literal(line));
    }
}
