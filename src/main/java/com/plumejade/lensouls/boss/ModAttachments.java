package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * NeoForge 实体附件注册。
 * <p>
 * BOSS 韧性数据通过附件随实体 NBT 持久化：
 * 实体卸载时写入，实体加入世界时读回（见 {@link BossToughnessManager}），
 * 否则「定身中存档→重进」会导致定身丢失且 NoGravity 永久残留。
 */
public class ModAttachments {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LenSouls.MODID);

    /** BOSS 韧性数据（requiredHits 仅为占位默认值，实际值由注册时计算并随数据序列化） */
    public static final Supplier<AttachmentType<BossToughnessData>> BOSS_TOUGHNESS =
            ATTACHMENT_TYPES.register("boss_toughness",
                    () -> AttachmentType.serializable(() -> new BossToughnessData(1)).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
