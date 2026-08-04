package com.plumejade.lensouls.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 回复药水：使用次数以耐久条实现。
 * <p>
 * 剩余耐久 &gt; 1 时右键可用一次（消耗 1 耐久），回复饥饿值和饱和度：
 * 饥饿值 &lt; 20 时补满 20 饥饿 + 20 饱和度；饥饿值已满（≥20）时不可用。
 * 总耐久 = 1 + 已到访维度数（初始主世界 1 个 → 2 耐久，可用 1 次；每多探索一个维度 +1 总耐久）。
 * 已到访维度列表存于物品自身（死亡/换人后不丢失），背包任意槽位每 20 tick 记录当前维度。
 * 已消耗耐久每 30 秒自动回复 1 点（动态恢复，背包中任意槽位生效）。
 */
public class HealPotionItem extends Item {

    /** 恢复间隔（tick）：30 秒 = 600 ticks */
    public static final long REGEN_INTERVAL_TICKS = 600L;

    /** stack CUSTOM_DATA 键：已到访维度列表（ListTag<String>，entity id 全名） */
    public static final String KEY_VISITED_LIST = "lensouls:visited_list";
    /** stack CUSTOM_DATA 键：上次回复时间戳（world gameTime） */
    public static final String KEY_LAST_REGEN = "lensouls:last_regen";
    /** 玩家 persistent data 键（仅旧存档迁移用） */
    public static final String PLAYER_KEY = "lensouls";
    /** 旧版本玩家 persistent data 列表键 */
    public static final String LEGACY_PLAYER_LIST = "visited_dimensions";

    public HealPotionItem(Properties properties) {
        super(properties.stacksTo(1).durability(2));
    }

    /** 总耐久 = 1 + 已到访维度数（每维度 +1 次使用次数） */
    @Override
    public int getMaxDamage(ItemStack stack) {
        return 1 + getVisitedCount(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide) {
            // 客户端本地播放音效（与项目其他自定义音效一致，避免服务端广播丢失）
            int remaining = stack.getMaxDamage() - stack.getDamageValue();
            if (remaining > 1 && player.getFoodData().getFoodLevel() < 20) {
                float vol = 1.0f + (level.random.nextFloat() * 2 - 1) * 0.15f;
                float pitch = 1.0f + (level.random.nextFloat() * 2 - 1) * 0.15f;
                player.playSound(com.plumejade.lensouls.sound.ModSounds.HEAL_USE.get(), vol, pitch);
            }
            return InteractionResultHolder.pass(stack);
        }

        // 使用前同步最新维度数（避免刚拿起未到 tick 更新的情况）
        HealPotionItem.recordVisited(stack, player);
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        if (remaining <= 1) {
            player.displayClientMessage(Component.translatable("message.lensouls.heal_potion.empty"), true);
            return InteractionResultHolder.fail(stack);
        }
        // 削弱：饥饿值已满（≥20）时无法使用
        if (player.getFoodData().getFoodLevel() >= 20) {
            player.displayClientMessage(Component.translatable("message.lensouls.heal_potion.full"), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.setDamageValue(stack.getDamageValue() + 1);
        // 回复饥饿值补满 20，饱和度同步补满（setSaturation 上限受饥饿值约束）
        FoodData food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(20.0f);
        // 使用后重置恢复计时（下次从当前时刻起 30 秒回复 1 点）
        setLastRegen(stack, level.getGameTime());
        return InteractionResultHolder.success(stack);
    }

    /**
     * 动态恢复耐久：每 30 秒回复 1 点（每 20 tick 低频检查）。
     * 背包任意槽位生效；已满耐久不处理；恢复上限不超当前总耐久。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity,
                              int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!(entity instanceof Player player)) return;
        if (entity.tickCount % 20 != 0) return;

        // 记录当前维度到物品（信息跟随物品，死亡/换人/放箱后不丢失）
        recordVisited(stack, player);

        int damage = stack.getDamageValue();
        if (damage <= 0) return;

        long now = level.getGameTime();
        long last = getLastRegen(stack);
        if (last == 0) {
            // 旧存档物品无时间戳：从当前时刻开始计时
            setLastRegen(stack, now);
            return;
        }
        if (now - last >= REGEN_INTERVAL_TICKS) {
            stack.setDamageValue(damage - 1);
            setLastRegen(stack, now);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        tooltip.add(Component.translatable("item.lensouls.heal_potion.desc", Math.max(0, remaining - 1)));
        tooltip.add(Component.translatable("item.lensouls.heal_potion.dim_count", getVisitedCount(stack)));
    }

    // ========== 维度计数 ==========

    public static long getLastRegen(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        return tag.getLong(KEY_LAST_REGEN);
    }

    public static void setLastRegen(ItemStack stack, long time) {
        CompoundTag tag = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag.getLong(KEY_LAST_REGEN) != time) {
            tag.putLong(KEY_LAST_REGEN, time);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
        }
    }

    /** stack 上记录的已到访维度数（未记录过默认 1，即主世界） */
    public static int getVisitedCount(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tag.contains(KEY_VISITED_LIST, Tag.TAG_LIST)) {
            return Math.max(1, tag.getList(KEY_VISITED_LIST, Tag.TAG_STRING).size());
        }
        return 1;
    }

    /**
     * 记录当前维度到物品 stack，返回已到访维度总数（≥1）。
     * 首次记录时若 stack 无列表，则从玩家旧 persistent data 迁移。
     */
    public static int recordVisited(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        boolean dirty = false;
        ListTag list;
        if (tag.contains(KEY_VISITED_LIST, Tag.TAG_LIST)) {
            list = tag.getList(KEY_VISITED_LIST, Tag.TAG_STRING);
        } else {
            list = migrateLegacyList(player);
            dirty = true;
        }

        String current = player.level().dimension().location().toString();
        boolean has = false;
        for (Tag t : list) {
            if (t.getAsString().equals(current)) {
                has = true;
                break;
            }
        }
        if (!has) {
            list.add(StringTag.valueOf(current));
            dirty = true;
        }
        if (dirty) {
            tag.put(KEY_VISITED_LIST, list);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(tag));
        }
        return Math.max(1, list.size());
    }

    /** 旧版本（玩家 persistent data 中 visited_dimensions 列表）迁移，无则空列表 */
    private static ListTag migrateLegacyList(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag lensouls = root.getCompound(PLAYER_KEY);
        for (String key : new String[]{KEY_VISITED_LIST, LEGACY_PLAYER_LIST}) {
            if (lensouls.contains(key, Tag.TAG_LIST)) {
                return lensouls.getList(key, Tag.TAG_STRING);
            }
        }
        return new ListTag();
    }
}
