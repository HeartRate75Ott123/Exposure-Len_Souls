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

    /**
     * 浓雾的治愈的「在场 tick 累计器」（见 {@link BossHealTimer}）。
     * <p>
     * 与效果实例的 {@code duration} 完全解耦：刷新 buff（重设 duration）不会重置累计进度，
     * 换维度/关卡时钟也不影响节奏。<b>非序列化</b>是刻意的——区块卸载/重载后实体附件重建，
     * 等效于「断档」⇒ 重新等满 1 秒再回第一跳，不做断档补偿，因此不会出现「重载即暴血」。
     * 无需同步；随实体一并销毁。
     */
    public static final Supplier<AttachmentType<BossHealTimer>> BOSS_HEAL_TIMER =
            ATTACHMENT_TYPES.register("boss_heal_timer",
                    () -> AttachmentType.builder(BossHealTimer::new).build());

    /**
     * 照片弹幕标记的<b>客户端可见通道</b>（单一事实来源见
     * {@link com.plumejade.lensouls.util.PhotoProjMarker}）。
     * <p>
     * 弹幕标记原本只写实体 {@code persistentData}——那份数据<b>不随实体同步到客户端</b>
     * （NeoForge 只在实体存档写入/读取里用它），所以客户端渲染器读不到「这是不是我们的弹幕」。
     * 这里注册一个<b>同步布尔附件</b>：服务端 {@code setData} + {@code syncData} 之后由
     * NeoForge {@code AttachmentSync} 发给追踪该实体的客户端；实体刚开始被追踪时
     * （{@code syncInitialEntityAttachments}）也会补发初值，所以「生成即标记再入世」这条顺序是安全的。
     * 弹幕都是瞬态实体，不需要序列化（不写 {@code serialize}）。
     */
    public static final Supplier<AttachmentType<Boolean>> PHOTO_PROJ =
            ATTACHMENT_TYPES.register("photo_proj",
                    () -> AttachmentType.<Boolean>builder(() -> false)
                            .sync(net.minecraft.network.codec.ByteBufCodecs.BOOL).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
