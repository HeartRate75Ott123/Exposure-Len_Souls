package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * 时空回溯处理器。
 * <p>
 * 被动：LivingDamageEvent.Pre 检测致命伤，消耗回溯照片保命。
 * 主动：由 CameraInputHandler → TemporalRecallTriggerPacket 触发。
 * <p>
 * 判定以照片数据（lensouls:snapshot）为准，不检查当前能力。
 * 支持 StackedPhotographsItem 内部嵌套照片的查找与消耗。
 */
public class TemporalRecallHandler {

    /**
     * 照片查找结果：持有回溯照片的槽位和索引信息。
     * 用于将"查找"和"消耗"两阶段解耦，正确处理 StackedPhotographsItem 的内外层关系。
     */
    private record PhotoFindResult(ItemStack outerStack, int slotIndex, int innerIndex) {
        /** 是 StackedPhotographsItem 内部的子照片 */
        boolean isStacked() { return innerIndex >= 0; }
        boolean isEmpty() { return outerStack.isEmpty(); }
        static final PhotoFindResult EMPTY = new PhotoFindResult(ItemStack.EMPTY, -1, -1);
    }

    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getEntity().level().isClientSide) return;

        // 不致命 → 跳过
        if (event.getNewDamage() < player.getHealth()) return;

        // 扫描背包找回溯照片，返回内外层完整定位信息
        PhotoFindResult result = findTemporalPhoto(player);
        if (result.isEmpty()) return;

        // 先抵消伤害（无论 snapshot 读取是否成功都不致死）
        event.setNewDamage(0);

        // 从实际持有 snapshot 数据的照片读取快照
        TemporalSnapshot snapshot = readSnapshot(result);
        if (snapshot != null) {
            consumePhoto(player, result);
            snapshot.apply(player);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("ability.lensouls.temporal_recall.triggered")
                            .copy().withStyle(net.minecraft.ChatFormatting.GREEN), true);
            // 回溯后易伤叠加（每5次+1级，持续20s，窗口5min）
            applyRecallVulnerability(player);
        }
    }

    /** 时空回溯易伤：每5次回溯叠1层，持续20s，窗口5min */
    private static final String TAG_COUNT = "lensouls:recall_count";
    private static final String TAG_VULN = "lensouls:recall_vuln_amp";
    private static final String TAG_WINDOW = "lensouls:recall_window_end";
    private static final String TAG_VULN_END = "lensouls:recall_vuln_end";

    private static void applyRecallVulnerability(ServerPlayer player) {
        var tag = player.getPersistentData();
        long now = player.level().getGameTime();
        int count = tag.getInt(TAG_COUNT) + 1;
        tag.putInt(TAG_COUNT, count);

        // 窗口结束时间（5min = 6000 tick）
        long windowEnd = now + 6000;
        tag.putLong(TAG_WINDOW, windowEnd);

        // 每5次叠1层
        int amp = count / 5;
        tag.putInt(TAG_VULN, amp);
        tag.putLong(TAG_VULN_END, now + 400); // 20s

        // 给玩家一个视觉提示（虚弱效果）
        if (amp > 0) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.WEAKNESS, 400, amp - 1, false, true, true));
        }
    }

    /** 真正死亡时重置回溯数据 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getPersistentData().remove(TAG_COUNT);
            player.getPersistentData().remove(TAG_VULN);
            player.getPersistentData().remove(TAG_WINDOW);
            player.getPersistentData().remove(TAG_VULN_END);
        }
    }

    /** 易伤增伤：在窗口内且易伤未过期时增伤 */
    @SubscribeEvent
    public static void onVulnerabilityDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        var tag = player.getPersistentData();
        long now = player.level().getGameTime();
        long windowEnd = tag.getLong(TAG_WINDOW);
        long vulnEnd = tag.getLong(TAG_VULN_END);
        if (now > windowEnd || now > vulnEnd) return;
        int amp = tag.getInt(TAG_VULN);
        if (amp <= 0) return;
        float multiplier = 1.0f + 0.2f * amp;
        event.setNewDamage(event.getNewDamage() * multiplier);
    }

    /** 公开消费入口——供主动触发包和被动保命共用 */
    public static TemporalSnapshot consumeTemporalPhoto(ServerPlayer player) {
        PhotoFindResult result = findTemporalPhoto(player);
        if (result.isEmpty()) return null;
        TemporalSnapshot snapshot = readSnapshot(result);
        if (snapshot != null) {
            consumePhoto(player, result);
        }
        return snapshot;
    }

    // ========== 快照读取 ==========

    /**
     * 从查找结果中读取快照。
     * StackedPhotographsItem 必须读内层照片的 snapshot 数据（外层没有）。
     */
    private static TemporalSnapshot readSnapshot(PhotoFindResult result) {
        if (!result.isStacked()) {
            return TemporalSnapshot.fromPhoto(result.outerStack());
        }
        ItemStack inner = getInnerPhoto(result.outerStack(), result.innerIndex());
        return inner.isEmpty() ? null : TemporalSnapshot.fromPhoto(inner);
    }

    // ========== 查找 ==========

    /**
     * 扫描玩家背包，返回第一个有回溯快照的 {@link PhotoFindResult}。
     * 直接搜索单张照片，也会深入 StackedPhotographsItem 内部查找。
     */
    private static PhotoFindResult findTemporalPhoto(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 单张照片直接匹配
            if (TemporalSnapshot.hasSnapshot(stack)) {
                return new PhotoFindResult(stack, i, -1);
            }

            // StackedPhotographsItem 内部检查
            if (isStackedPhotoItem(stack)) {
                int innerIdx = findSnapshotIndexInStacked(stack);
                if (innerIdx >= 0) {
                    return new PhotoFindResult(stack, i, innerIdx);
                }
            }
        }
        return PhotoFindResult.EMPTY;
    }

    /** 判断物品是否为 StackedPhotographsItem */
    private static boolean isStackedPhotoItem(ItemStack stack) {
        return stack.getItem().getClass().getName().contains("StackedPhotographsItem");
    }

    /**
     * 反射遍历 StackedPhotographsItem 内部照片，返回第一个有 snapshot 内容的索引。
     * @return 内层索引，-1 表示未找到
     */
    private static int findSnapshotIndexInStacked(ItemStack stackedStack) {
        try {
            Class<?> itemClass = stackedStack.getItem().getClass();
            var getPhotos = itemClass.getMethod("getPhotographs", ItemStack.class);
            Object photos = getPhotos.invoke(stackedStack.getItem(), stackedStack);

            Class<?> photosClass = Class.forName("io.github.mortuusars.exposure.world.item.component.StackedPhotographs");
            var sizeMethod = photosClass.getMethod("size");
            int size = (int) sizeMethod.invoke(photos);

            var getItemMethod = photosClass.getMethod("getItemUnsafe", int.class);
            for (int i = 0; i < size; i++) {
                ItemStack inner = (ItemStack) getItemMethod.invoke(photos, i);
                if (TemporalSnapshot.hasSnapshot(inner)) return i;
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[时空回溯] StackedPhotographs 查找失败", e);
        }
        return -1;
    }

    /** 反射获取 StackedPhotographsItem 内指定索引的子照片 */
    private static ItemStack getInnerPhoto(ItemStack stackedStack, int index) {
        try {
            Class<?> itemClass = stackedStack.getItem().getClass();
            var getPhotos = itemClass.getMethod("getPhotographs", ItemStack.class);
            Object photos = getPhotos.invoke(stackedStack.getItem(), stackedStack);
            Class<?> photosClass = Class.forName("io.github.mortuusars.exposure.world.item.component.StackedPhotographs");
            var getItemMethod = photosClass.getMethod("getItemUnsafe", int.class);
            return (ItemStack) getItemMethod.invoke(photos, index);
        } catch (Exception e) {
            LenSouls.LOGGER.error("[时空回溯] 获取内层照片失败", e);
            return ItemStack.EMPTY;
        }
    }

    // ========== 消耗 ==========

    /**
     * 消耗照片。普通照片 shrink，堆叠照片用已知索引 remove(index)。
     */
    private static void consumePhoto(ServerPlayer player, PhotoFindResult result) {
        if (result.isStacked()) {
            consumeFromStacked(player, result.outerStack(), result.innerIndex());
        } else {
            ItemStack stack = result.outerStack();
            stack.shrink(1);
            if (stack.isEmpty()) {
                player.getInventory().setItem(result.slotIndex(), ItemStack.EMPTY);
            }
        }
    }

    /**
     * 反射调用 StackedPhotographs.remove(index) 消耗指定索引的子照片。
     * 使用已知 ${@code knownIndex}，不再二次遍历。
     */
    private static void consumeFromStacked(ServerPlayer player, ItemStack stackedStack, int knownIndex) {
        try {
            Class<?> itemClass = stackedStack.getItem().getClass();
            var getPhotos = itemClass.getMethod("getPhotographs", ItemStack.class);
            Object photos = getPhotos.invoke(stackedStack.getItem(), stackedStack);

            Class<?> photosClass = Class.forName("io.github.mortuusars.exposure.world.item.component.StackedPhotographs");
            var removeMethod = photosClass.getMethod("remove", int.class);
            Object newPhotos = removeMethod.invoke(photos, knownIndex);

            var sizeMethod = photosClass.getMethod("size");
            int newSize = (int) sizeMethod.invoke(newPhotos);
            var setMethod = itemClass.getMethod("setPhotographs", ItemStack.class, photosClass);

            if (newSize <= 0) {
                player.getInventory().removeItem(stackedStack);
            } else if (newSize == 1) {
                // 剩1张时提取为独立物品
                var getItem0 = photosClass.getMethod("getItemUnsafe", int.class);
                ItemStack remaining = (ItemStack) getItem0.invoke(newPhotos, 0);
                player.getInventory().removeItem(stackedStack);
                if (!player.addItem(remaining.copy())) {
                    player.drop(remaining.copy(), false);
                }
            } else {
                setMethod.invoke(stackedStack.getItem(), stackedStack, newPhotos);
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[时空回溯] StackedPhotographs 消耗失败", e);
        }
    }
}
