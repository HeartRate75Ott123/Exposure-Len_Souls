package com.plumejade.lensouls.feather;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * 羽毛槽的玩家附件注册。
 * <p>
 * {@code serializable} —— 随玩家 NBT 持久化（存档/重进都在）；
 * <b>{@code copyOnDeath}</b> —— 死亡重生时把数据整体复制给新玩家实例。
 * 后者是「生存装入即永久锁定」的必要条件：NeoForge 复活只重建玩家实体，
 * 不 copy 的话锁定位与槽内容会在死后全部丢失（羽毛就等于白装）。
 * <p>
 * 与 {@link com.plumejade.lensouls.boss.ModAttachments} 同款写法（那个是实体附件）。
 */
public class FeatherAttachments {

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LenSouls.MODID);

    /** 5 个羽毛槽（内容 + 永久锁定标记） */
    public static final Supplier<AttachmentType<FeatherSlotData>> FEATHER_SLOTS =
            TYPES.register("feather_slots",
                    () -> AttachmentType.serializable(FeatherSlotData::new).copyOnDeath().build());

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    /**
     * 取玩家的羽毛槽数据（缺失时返回默认实例，永远不会 null）。
     * <p>
     * <b>只在服务端调用</b>：这个附件刻意不做 {@code sync}——界面所需的槽内容由
     * {@code FeatherSlotMenu} 的槽位同步，锁定标记走 {@code ContainerData}，
     * 客户端不需要也不应该自己持有这份权威数据。
     */
    public static FeatherSlotData get(Player player) {
        return player.getData(FEATHER_SLOTS);
    }
}
