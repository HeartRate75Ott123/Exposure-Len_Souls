package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

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
        }
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
