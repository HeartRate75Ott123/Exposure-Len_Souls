package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.entity.BossPhantomManager;
import com.plumejade.lensouls.entity.BossPhantomType;
import com.plumejade.lensouls.gui.ModMenus;
import com.plumejade.lensouls.item.LensoulItem;
import com.plumejade.lensouls.timer.TimerService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * G 键转换器触发包（C2S）。
 * <p>
 * 客户端按下 G 键时发送此包到服务端，
 * 服务端查找玩家背包中的转换器并自动激活第一个可用的镜魂。
 */
public class ConverterTriggerPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ConverterTriggerPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "converter_trigger"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterTriggerPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {},
                    buf -> new ConverterTriggerPacket()
            );

    @Override
    @NotNull
    public CustomPacketPayload.Type<ConverterTriggerPacket> type() {
        return TYPE;
    }

    /** 服务端处理器 */
    public static void handle(ConverterTriggerPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;

            var timer = TimerService.getInstance();
            var playerId = player.getUUID();

            // 查找玩家背包中的转换器
            ItemStack converter = ModMenus.findConverter(player);
            if (converter.isEmpty()) {
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.converter_not_found"));
                return;
            }

            // 从转换器 CustomData 读取镜魂列表
            CustomData customData = converter.get(DataComponents.CUSTOM_DATA);
            if (customData == null) {
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.converter_empty"));
                return;
            }

            CompoundTag tag = customData.copyTag();
            if (!tag.contains("ConverterItems", Tag.TAG_LIST)) {
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.converter_empty"));
                return;
            }

            ListTag itemsList = tag.getList("ConverterItems", Tag.TAG_COMPOUND);
            var access = player.registryAccess();

            // 转换器顶层映射：由 G 键读写，saveToStack 不覆盖
            CompoundTag slotUuids = tag.getCompound("SoulItemIds");
            CompoundTag cooldowns = tag.getCompound("SoulCooldowns");

            // 遍历寻找第一个可用镜魂
            boolean phantomActiveSkipped = false;
            for (int i = 0; i < itemsList.size(); i++) {
                var slotTag = itemsList.getCompound(i);
                ItemStack soulStack = ItemStack.parseOptional(access, slotTag);
                if (soulStack.isEmpty() || !(soulStack.getItem() instanceof LensoulItem soulItem)) {
                    continue;
                }

                int slot = slotTag.getByte("Slot") & 0xFF;
                String slotKey = "slot_" + slot;

                // UUID 存转换器顶层 SoulItemIds.slot_N（不被 saveToStack 覆盖）
                String itemUuid = slotUuids.contains(slotKey, Tag.TAG_STRING)
                        ? slotUuids.getString(slotKey) : null;
                if (itemUuid == null) {
                    itemUuid = java.util.UUID.randomUUID().toString();
                    slotUuids.putString(slotKey, itemUuid);
                    tag.put("SoulItemIds", slotUuids);
                    converter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                String cooldownId = "soul_item_" + itemUuid;
                // 双源冷却检测：TimerService（内存）+ SoulCooldowns（持久化）
                boolean onCooldown = timer.isActive(playerId, cooldownId);
                if (!onCooldown && cooldowns.contains(itemUuid, Tag.TAG_COMPOUND)) {
                    CompoundTag cd = cooldowns.getCompound(itemUuid);
                    long end = cd.getLong("end");
                    long now = player.level().getGameTime();
                    if (end > now) {
                        timer.start(playerId, cooldownId, end - now);
                        onCooldown = true;
                    }
                }
                if (onCooldown) continue;

                // ---- BOSS 镜魂：已有活跃幻灵时跳过，不进冷却 ----
                float dmgMult = soulItem.getDamageMultiplier();
                boolean slowness = soulItem.shouldApplySlowness();
                var element = soulItem.getElement();
                String descId = soulStack.getDescriptionId();
                BossPhantomType phantomType = BossPhantomType.fromSoulItem(dmgMult, slowness, element);
                if (phantomType != null && phantomType.isModLoaded() && player instanceof ServerPlayer serverPlayer
                        && BossPhantomManager.getInstance().hasActivePhantom(serverPlayer.getUUID())) {
                    phantomActiveSkipped = true;
                    continue;
                }

                // ---- 启动冷却（独立于效果，低级启用后也进入冷却） ----
                int itemCooldownTicks = soulItem.getCooldownTicks();
                timer.start(playerId, cooldownId, itemCooldownTicks);
                long endTime = player.level().getGameTime() + itemCooldownTicks;
                CompoundTag cdEntry = new CompoundTag();
                cdEntry.putLong("end", endTime);
                cdEntry.putInt("dur", itemCooldownTicks);
                cooldowns.put(itemUuid, cdEntry);
                tag.put("SoulCooldowns", cooldowns);
                converter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                // ---- BOSS 镜魂：走虚影幻灵序列 ----
                if (phantomType != null && phantomType.isModLoaded() && player instanceof ServerPlayer serverPlayer) {
                    BossPhantomManager.getInstance().startPhantom(serverPlayer, phantomType, descId);

                    // 反馈：虚影已降临
                    Component soulDisplay = Component.translatable(descId);
                    player.sendSystemMessage(
                            Component.translatable("message.lensouls.phantom_start", soulDisplay));
                } else {
                    // ---- 基础镜魂 / BOSS 模组未加载：直接施加效果 ----
                    int amplifier = soulItem.getAmplifier(soulStack);
                    var effectHolder = soulItem.getEffectHolder();
                    int durationTicks = Config.DEFAULT_DURATION.get() * 20;
                    player.addEffect(new MobEffectInstance(
                            effectHolder, durationTicks, amplifier, false, false, false
                    ));
                    // 始终设置自定义名称（即使效果未变更），确保后续覆盖正确
                    ElementInfusionEffect.setPlayerData(player, soulItem.getElement(), soulItem.shouldApplySlowness(),
                            dmgMult > 1.0f || slowness ? descId : null);

                    String soulId = soulItem.getElement().getSerializedName();
                    Component soulDisplay = dmgMult > 1.0f || slowness
                            ? Component.translatable(descId)
                            : Component.translatable("element.lensouls." + soulId);
                    player.sendSystemMessage(
                            Component.translatable("message.lensouls.soul_activated", soulDisplay));
                }

                // 如果打开了转换器界面，刷新容器槽
                if (player.containerMenu instanceof com.plumejade.lensouls.gui.ConverterMenu menu) {
                    menu.getSoulContainer().setItem(slot, soulStack);
                }
                return;
            }

            // 全部冷却 / 幻灵表演中 / 无镜魂
            if (phantomActiveSkipped) {
                player.displayClientMessage(
                        Component.translatable("message.lensouls.converter_phantom_active")
                                .copy().withStyle(net.minecraft.ChatFormatting.WHITE), true);
            } else {
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.converter_all_cooldown"));
            }
        });
    }

}
