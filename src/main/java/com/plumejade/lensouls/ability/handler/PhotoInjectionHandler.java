package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.util.TemporalSnapshot;
import com.plumejade.lensouls.enchantment.ModEnchantments;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

/**
 * 拍照数据注入处理器。
 * <p>
 * 通过反射监听 Exposure 的 {@code FrameAddedEvent}（照片成功拍摄时触发），
 * 将当前能力的特定数据注入到照片物品中。
 * 改名为绿色 "照片(能力名)"。
 * <p>
 * 不再使用多 tick 扫描快照比对方式，改为事件触发 + 单次查找。
 */
public class PhotoInjectionHandler {

    // ========== 反射注册 ==========

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventClass = Class.forName("io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent");
            Method getCameraHolderEntity = eventClass.getMethod("getCameraHolderEntity");

            Method addListener = NeoForge.EVENT_BUS.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, Consumer.class);

            addListener.invoke(NeoForge.EVENT_BUS, EventPriority.NORMAL, false, eventClass,
                    (Consumer<Object>) event -> {
                        try {
                            Entity entity = (Entity) getCameraHolderEntity.invoke(event);
                            if (!(entity instanceof ServerPlayer player)) return;

                            // 检查当前能力
                            AbilityManager am = AbilityManager.getInstance();
                            AbilityType ability = am.getEnabled(player);
                            if (ability == null) return;
                            if (ability == AbilityType.TIME_STOP) {
                                // TIME_STOP 由 TimeFreezeHandler 处理
                                return;
                            }
                            if (ability == AbilityType.VITAL_STRIKE) {
                                // VITAL_STRIKE 不产生照片，由 VitalStrikeHandler 处理
                                return;
                            }

                            // 所有相机能力都需要摄魂术附魔
                            ItemStack hand = player.getMainHandItem();
                            if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), hand) <= 0) {
                                return;
                            }

                            // WEAKNESS_LENS / SPATIAL_WARP / TEMPORAL_RECALL
                            // 首次解锁时 setUnlocked 已发送描述

                            // 将能力写入相机物品，供拍立得 mixin 在出片时直接读取
                            CompoundTag cameraTag = hand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                            cameraTag.putString("lensouls:capture_ability", ability.getId());
                            hand.set(DataComponents.CUSTOM_DATA, CustomData.of(cameraTag));

                            // 拍立得走 capture_ability 路径，不入 pendingQueue
                            // 只有走 Lightroom 冲洗的常规相机才需要队列——入队等待照片出现
                            ResourceLocation camId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hand.getItem());
                            if (!"exposure_polaroid:instant_camera".equals(camId.toString())) {
                                enqueue(player.getUUID(), ability);
                            }

                        } catch (Exception e) {
                            LenSouls.LOGGER.error("[PhotoInject] 处理 FrameAddedEvent 失败", e);
                        }
                    });

        } catch (Exception e) {
            LenSouls.LOGGER.error("[PhotoInject] 注册失败（Exposure 未加载？）", e);
        }
    }

    // ========== 待注入队列（快门时能力快照）==========

    /**
     * playerUUID → FIFO 队列，记录按下快门时的能力。
     * 用队列而非计数器，因为照片冲印是异步的，冲出来时能力可能已切换。
     */
    private static final Map<UUID, LinkedList<AbilityType>> pendingQueue = new HashMap<>();

    /** FrameAddedEvent 回调中调用：记录按下快门时的能力 */
    public static void enqueue(UUID playerUuid, AbilityType ability) {
        pendingQueue.computeIfAbsent(playerUuid, k -> new LinkedList<>()).addLast(ability);
    }

    /**
     * LightroomInjectMixin 调用：取出队列中最早的一笔待注入能力。
     * 以 FIFO 顺序匹配 FrameAddedEvent 的顺序。
     */
    public static AbilityType pollAbility(UUID playerUuid) {
        LinkedList<AbilityType> queue = pendingQueue.get(playerUuid);
        if (queue == null || queue.isEmpty()) return null;
        AbilityType ability = queue.pollFirst();
        if (queue.isEmpty()) {
            pendingQueue.remove(playerUuid);
        }
        return ability;
    }

    // ========== 队列清理 ==========

    /**
     * 每 tick 清理过期队列条目。
     * <p>
     * PolaroidPrintMixin 和 LightroomInjectMixin 分别在出片/冲印时 drain 队列，
     * 本 tick 处理器只兜底清理断线玩家和过期的空队列，<b>不再做注入</b>。
     * 注入只发生在 PolaroidPrintMixin（通过 {@code capture_ability}）和
     * LightroomInjectMixin（通过 {@code pollAbility}）中，避免
     * tick 扫描器在 printPhotograph 出片前误注入玩家背包中的旧照片。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (pendingQueue.isEmpty()) return;

        Iterator<Map.Entry<UUID, LinkedList<AbilityType>>> it = pendingQueue.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LinkedList<AbilityType>> entry = it.next();
            ServerPlayer player = getPlayerByUUID(entry.getKey());
            LinkedList<AbilityType> queue = entry.getValue();

            // 断线或已死亡 → 清理
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }

            // 队列已被 mixin  drain 为空 → 清理
            if (queue.isEmpty()) {
                it.remove();
            }
        }
    }

    /** 获取在线玩家 */
    private static ServerPlayer getPlayerByUUID(UUID uuid) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return null;
        return server.getPlayerList().getPlayer(uuid);
    }

    /** 扫描玩家背包中第一张未被注入的照片（自动分裂堆叠，每张独立占槽） */
    private static boolean tryInjectFirstUnmarked(ServerPlayer player, AbilityType ability) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isExposurePhoto(stack)) continue;

            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.getBoolean(INJECTED_MARKER)) continue;

            // StackedPhotographs 不处理，避免乱注入
            if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.StackedPhotographsItem) {
                continue;
            }

            // 普通照片堆叠分裂
            ItemStack target = stack;
            if (stack.getCount() > 1) {
                target = stack.split(1);
            }

            doInject(player, target, ability);

            if (target != stack) {
                target.setCount(1);
                if (!player.getInventory().add(target)) {
                    player.drop(target, false);
                }
            }
            return true;
        }
        return false;
    }

    // ========== 注入逻辑 ==========

    private static final String INJECTED_MARKER = "lensouls:injected";
    private static final String ABILITY_TYPE_KEY = "lensouls:ability_type";
    private static final String SPATIAL_WARP_POS = "lensouls:spatial_warp_pos";

    /**
     * 注入能力数据到照片并改名。注入后强制单张，不参与堆叠。
     */
    private static void doInject(ServerPlayer player, ItemStack stack, AbilityType ability) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.getBoolean(INJECTED_MARKER)) return; // 防重入

        tag.putBoolean(INJECTED_MARKER, true);
        tag.putString(ABILITY_TYPE_KEY, ability.getId());

        switch (ability) {
            case SPATIAL_WARP -> {
                CompoundTag posTag = new CompoundTag();
                posTag.putDouble("x", player.getX());
                posTag.putDouble("y", player.getY());
                posTag.putDouble("z", player.getZ());
                tag.put(SPATIAL_WARP_POS, posTag);
            }
            case TEMPORAL_RECALL -> {
                TemporalSnapshot snapshot = TemporalSnapshot.capture(player);
                tag.put("lensouls:snapshot", snapshot.toTag());
            }
            default -> {} // WEAKNESS_LENS：仅标记，无额外数据
        }

        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.setCount(1); // 强制单张，不与普通照片堆叠
        setPhotoName(stack, ability);
    }

    // ========== 照片命名 ==========

    /** 设置照片名为绿色 "照片(能力名)" */
    private static void setPhotoName(ItemStack stack, AbilityType ability) {
        MutableComponent name = Component.literal("")
                .append(Component.literal("照片("))
                .append(Component.translatable("ability.lensouls." + ability.getId() + ".name"))
                .append(Component.literal(")"))
                .withStyle(net.minecraft.ChatFormatting.GREEN)
                .withStyle(s -> s.withItalic(false).withBold(false));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    // ========== 工具方法 ==========

    private static boolean isExposurePhoto(ItemStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.toString().equals("exposure:photograph")
                || id.toString().equals("exposure:stacked_photographs");
    }
}
