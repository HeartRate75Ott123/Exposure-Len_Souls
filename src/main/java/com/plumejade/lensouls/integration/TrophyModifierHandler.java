package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
            case "toughness_bonus" -> 1.0f + rnd.nextInt(3) * 0.5f; // 1.0 / 1.5 / 2.0
            case "stun_extend" -> 1.2f + rnd.nextFloat() * 0.4f;    // 1.2~1.6
            case "invincible_shorten" -> 0.5f + rnd.nextFloat() * 0.3f; // 0.5~0.8
            default -> 1.0f;
        };
        return new TrophyMod(type, value);
    }

    /** 每 tick 检查：首次装备时生成修饰符 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            // 遍历所有 Curios 槽位而非仅 head
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) return;
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            // 获取所有槽位类型
            Object curios = handler.getClass().getMethod("getCurios").invoke(handler);
            if (curios instanceof java.util.Map<?, ?> map) {
                for (var entry : map.entrySet()) {
                    Object stacks = entry.getValue().getClass().getMethod("getStacks").invoke(entry.getValue());
                    int slots = (int) stacks.getClass().getMethod("getSlots").invoke(stacks);
                    for (int i = 0; i < slots; i++) {
                        ItemStack stack = (ItemStack) stacks.getClass().getMethod("getStackInSlot", int.class).invoke(stacks, i);
                        if (stack.isEmpty()) continue;
                        // 只处理 TF 奖杯物品
                        String regName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (!regName.contains("twilightforest") || !regName.contains("trophy")) continue;
                        LenSouls.LOGGER.info("[Trophy] 发现奖杯: {}", regName);
                        if (getModifier(stack) != null) continue;
                        TrophyMod mod = rollModifier();
                        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                        CompoundTag mt = new CompoundTag();
                        mt.putString("type", mod.type);
                        mt.putFloat("value", mod.value);
                        tag.put(TAG, mt);
                        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        // 写回槽位触发同步
                        try {
                            stacks.getClass().getMethod("setStackInSlot", int.class, ItemStack.class).invoke(stacks, i, stack);
                        } catch (Exception ignored) {}
                        LenSouls.LOGGER.info("[Trophy] 生成修饰符 {} x{} 于 {}", mod.type(), mod.value(), stack.getHoverName().getString());
                    }
                }
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[Trophy] tick 失败", e);
        }
    }

    private static java.util.List<?> findCurios(ServerPlayer player, String slot) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) {
                LenSouls.LOGGER.warn("[Trophy] Curios 库存未找到");
                return null;
            }
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            var result = (java.util.List<?>) handler.getClass().getMethod("findCurios", String.class).invoke(handler, slot);
            LenSouls.LOGGER.info("[Trophy] findCurios({}) 返回: {}", slot, result == null ? "null" : result.size() + "个");
            return result;
        } catch (Exception e) {
            LenSouls.LOGGER.error("[Trophy] findCurios 失败", e);
            return null;
        }
    }

    public record TrophyMod(String type, float value) {}

    /** 应用修饰符到韧性数据 */
    public static float applyHitsModifier(ServerPlayer player, float baseHits) {
        float total = baseHits;
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) return total;
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            var results = (java.util.List<?>) handler.getClass()
                    .getMethod("findCurios", String.class).invoke(handler, "head");
            if (results == null) return total;
            for (Object r : results) {
                ItemStack stack = (ItemStack) r.getClass().getMethod("stack").invoke(r);
                TrophyMod m = getModifier(stack);
                if (m == null) continue;
                switch (m.type) {
                    case "toughness_bonus" -> total *= m.value;
                    case "stun_extend" -> {} // handled in stun
                    case "invincible_shorten" -> {} // handled in invincible
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    public static int applyStunModifier(ServerPlayer player, int baseStun) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) return baseStun;
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            var results = (java.util.List<?>) handler.getClass()
                    .getMethod("findCurios", String.class).invoke(handler, "head");
            if (results == null) return baseStun;
            for (Object r : results) {
                ItemStack stack = (ItemStack) r.getClass().getMethod("stack").invoke(r);
                TrophyMod m = getModifier(stack);
                if (m == null) continue;
                if ("stun_extend".equals(m.type)) return (int) (baseStun * m.value);
            }
        } catch (Exception ignored) {}
        return baseStun;
    }

    public static int applyInvincibleModifier(ServerPlayer player, int baseInvincible) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) return baseInvincible;
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            var results = (java.util.List<?>) handler.getClass()
                    .getMethod("findCurios", String.class).invoke(handler, "head");
            if (results == null) return baseInvincible;
            for (Object r : results) {
                ItemStack stack = (ItemStack) r.getClass().getMethod("stack").invoke(r);
                TrophyMod m = getModifier(stack);
                if (m == null) continue;
                if ("invincible_shorten".equals(m.type)) return Math.max(1, (int) (baseInvincible * m.value));
            }
        } catch (Exception ignored) {}
        return baseInvincible;
    }

    @SubscribeEvent
    public static void onTooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        TrophyMod m = getModifier(stack);
        if (m == null) return;
        String line = switch (m.type) {
            case "toughness_bonus" -> "§6韧性伤害 +" + String.format("%.1f", m.value - 1);
            case "stun_extend" -> "§6破刹时长 ×" + String.format("%.0f%%", m.value * 100);
            case "invincible_shorten" -> "§6白霸体时长 ×" + String.format("%.0f%%", m.value * 100);
            default -> "";
        };
        if (!line.isEmpty()) event.getToolTip().add(net.minecraft.network.chat.Component.literal(line));
    }
}
