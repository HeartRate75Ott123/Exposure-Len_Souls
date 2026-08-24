package com.plumejade.lensouls.network;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
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
 * 激活转换器指定镜魂（C2S）。
 * <p>
 * 长按菜单中鼠标悬停某个非冷却镜魂后松开时发送，服务端激活该槽位镜魂。
 */
public class ConverterMenuActivatePacket implements CustomPacketPayload {

    public static final Type<ConverterMenuActivatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "converter_activate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConverterMenuActivatePacket> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, p -> p.slot,
                    ConverterMenuActivatePacket::new);

    private final int slot;

    public ConverterMenuActivatePacket(int slot) {
        this.slot = slot;
    }

    @Override
    @NotNull
    public Type<ConverterMenuActivatePacket> type() {
        return TYPE;
    }

    /** 服务端处理：激活指定槽位镜魂 */
    public static void handle(ConverterMenuActivatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            activateSoul(player, packet.slot);
        });
    }

    /** 激活转换器指定槽位的镜魂（复用 G 键激活逻辑）。返回 true 表示已激活。 */
    private static boolean activateSoul(ServerPlayer player, int slotIndex) {
        ItemStack converter = ModMenus.findConverter(player);
        if (converter.isEmpty()) return false;

        CustomData customData = converter.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return false;
        CompoundTag tag = customData.copyTag();
        if (!tag.contains("ConverterItems", Tag.TAG_LIST)) return false;

        ListTag itemsList = tag.getList("ConverterItems", Tag.TAG_COMPOUND);
        var access = player.registryAccess();
        CompoundTag slotUuids = tag.getCompound("SoulItemIds");
        CompoundTag cooldowns = tag.getCompound("SoulCooldowns");
        var timer = TimerService.getInstance();
        var playerId = player.getUUID();

        for (int i = 0; i < itemsList.size(); i++) {
            var slotTag = itemsList.getCompound(i);
            int slot = slotTag.getByte("Slot") & 0xFF;
            if (slot != slotIndex) continue;

            ItemStack soulStack = ItemStack.parseOptional(access, slotTag);
            if (soulStack.isEmpty() || !(soulStack.getItem() instanceof LensoulItem soulItem)) return false;

            String slotKey = "slot_" + slot;
            String itemUuid = slotUuids.contains(slotKey, Tag.TAG_STRING)
                    ? slotUuids.getString(slotKey) : null;
            if (itemUuid == null) {
                itemUuid = java.util.UUID.randomUUID().toString();
                slotUuids.putString(slotKey, itemUuid);
                tag.put("SoulItemIds", slotUuids);
                converter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            String cooldownId = "soul_item_" + itemUuid;
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
            if (onCooldown) return false;

            // BOSS 镜魂：幻灵活跃时跳过
            var element = soulItem.getElement();
            String descId = soulStack.getDescriptionId();
            BossPhantomType phantomType = BossPhantomType.fromDescriptionId(descId);
            if (phantomType != null && phantomType.isModLoaded()
                    && BossPhantomManager.getInstance().hasActivePhantom(playerId)) {
                player.displayClientMessage(
                        Component.translatable("message.lensouls.converter_phantom_active")
                                .copy().withStyle(net.minecraft.ChatFormatting.WHITE), true);
                return false;
            }

            // 启动冷却
            int itemCooldownTicks = soulItem.getCooldownTicks();
            timer.start(playerId, cooldownId, itemCooldownTicks);
            long endTime = player.level().getGameTime() + itemCooldownTicks;
            CompoundTag cdEntry = new CompoundTag();
            cdEntry.putLong("end", endTime);
            cdEntry.putInt("dur", itemCooldownTicks);
            cooldowns.put(itemUuid, cdEntry);
            tag.put("SoulCooldowns", cooldowns);
            converter.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

            // BOSS 镜魂 → 幻灵
            if (phantomType != null && phantomType.isModLoaded()) {
                int amp = com.plumejade.lensouls.handler.AnvilUpgradeHandler.getAmplifier(soulStack);
                BossPhantomManager.getInstance().startPhantom(player, phantomType, descId, amp);
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.phantom_start",
                                Component.translatable(descId)));
            } else {
                // 基础镜魂
                int amplifier = soulItem.getAmplifier(soulStack);
                var effectHolder = soulItem.getEffectHolder();
                int durationTicks = Config.DEFAULT_DURATION.get() * 20;
                player.addEffect(new MobEffectInstance(effectHolder, durationTicks, amplifier, false, false, false));
                com.plumejade.lensouls.effect.ElementInfusionEffect.setPlayerData(player, soulItem.getElement(),
                        soulItem.shouldApplySlowness(),
                        soulItem.getDamageMultiplier() > 1.0f || soulItem.shouldApplySlowness() ? descId : null);
                String soulId = soulItem.getElement().getSerializedName();
                Component soulDisplay = soulItem.getDamageMultiplier() > 1.0f || soulItem.shouldApplySlowness()
                        ? Component.translatable(descId)
                        : Component.translatable("element.lensouls." + soulId);
                player.sendSystemMessage(
                        Component.translatable("message.lensouls.soul_activated", soulDisplay));
            }

            // 刷新容器槽（若转换器 GUI 打开）
            if (player.containerMenu instanceof com.plumejade.lensouls.gui.ConverterMenu menu) {
                menu.getSoulContainer().setItem(slot, soulStack);
            }
            return true;
        }
        return false;
    }
}
