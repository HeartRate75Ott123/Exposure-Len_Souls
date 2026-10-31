package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.ClientTetherCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;

/**
 * S2C：引力枪「牵引关系」全量同步（照抄原版 {@code ClientboundSetEntityLinkPacket} 的思路）。
 * <p>
 * 只带两组平行 int 数组：{@code owners[i]} 牵引 {@code targets[i]}；空数组 = 全部解除。
 * 服务端只在**关系变化时**发一次（开始 / 结束），不是每 tick 发。
 * <p>
 * 为什么这么做：以前客户端靠"被命中的那颗子弹"来决定画不画光束 —— 玩家跑快一点，
 * 子弹被移出跟踪范围/被移除重建，`targetId` 就重置成 -1，光束先"消失"，最后连牵引都没了。
 * 现在光束只依赖**两个真实实体**（拥有者、目标）的关系，和子弹存不存在完全解耦。
 * 这也正是原版拴绳的做法（{@code Leashable.leashHolder} + 两个 int 的同步包）。
 */
public class GravityTetherSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<GravityTetherSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gravity_tether_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GravityTetherSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(GravityTetherSyncPacket::encode, GravityTetherSyncPacket::new);

    private final int[] owners;
    private final int[] targets;

    public GravityTetherSyncPacket(Map<Integer, Integer> pairs) {   // targetId -> ownerId
        this.owners = new int[pairs.size()];
        this.targets = new int[pairs.size()];
        int i = 0;
        for (Map.Entry<Integer, Integer> e : pairs.entrySet()) {
            this.targets[i] = e.getKey();
            this.owners[i] = e.getValue();
            i++;
        }
    }

    private GravityTetherSyncPacket(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        this.owners = new int[n];
        this.targets = new int[n];
        for (int i = 0; i < n; i++) {
            this.owners[i] = buf.readVarInt();
            this.targets[i] = buf.readVarInt();
        }
    }

    private void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(owners.length);
        for (int i = 0; i < owners.length; i++) {
            buf.writeVarInt(owners[i]);
            buf.writeVarInt(targets[i]);
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GravityTetherSyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTetherCache.apply(packet.owners, packet.targets));
    }
}
