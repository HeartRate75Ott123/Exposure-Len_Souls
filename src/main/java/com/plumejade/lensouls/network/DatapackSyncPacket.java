package com.plumejade.lensouls.network;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.config.PhotoSetDefs;
import com.plumejade.lensouls.config.PhotoSetLoader;
import com.plumejade.lensouls.config.StaffItemLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C：数据包解析结果统一同步包。
 * <p>
 * 弱点倍率 / 攻击者活性 / 物品活性 / 照片套装成员 / 照片套装定义
 * 这些 {@code SimpleJsonResourceReloadListener} 只在服务端 {@code AddReloadListenerEvent} 触发，
 * 多人客机进程从不执行，导致 Jade 面板与照片套装 tooltip 为空。
 * <p>
 * 服务端在 {@code OnDatapackSyncEvent}（玩家加入 + /reload）发送本包，
 * 客户端 handler 填充各加载器的静态缓存，实现多人下客户端数据一致。
 */
public class DatapackSyncPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DatapackSyncPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "datapack_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DatapackSyncPacket> STREAM_CODEC =
            StreamCodec.ofMember(DatapackSyncPacket::encode, DatapackSyncPacket::new);

    private final Map<ResourceLocation, Map<ElementDamage, Float>> weaknesses;
    private final Map<ResourceLocation, Map<ElementDamage, Integer>> attackerElement;
    private final Map<ResourceLocation, Map<ElementDamage, Integer>> itemElementActivity;
    private final Map<ResourceLocation, List<String>> photoSetMembership;
    private final Map<String, PhotoSetDefs.SetDef> photoSetDefs;
    private final List<ResourceLocation> staffItems;

    public DatapackSyncPacket(
            Map<ResourceLocation, Map<ElementDamage, Float>> weaknesses,
            Map<ResourceLocation, Map<ElementDamage, Integer>> attackerElement,
            Map<ResourceLocation, Map<ElementDamage, Integer>> itemElementActivity,
            Map<ResourceLocation, List<String>> photoSetMembership,
            Map<String, PhotoSetDefs.SetDef> photoSetDefs,
            List<ResourceLocation> staffItems) {
        this.weaknesses = weaknesses;
        this.attackerElement = attackerElement;
        this.itemElementActivity = itemElementActivity;
        this.photoSetMembership = photoSetMembership;
        this.photoSetDefs = photoSetDefs;
        this.staffItems = staffItems;
    }

    /**
     * 构造当前服务端解析结果的同步包。
     * <p>
     * 供 OnDatapackSyncEvent（/reload 广播 + 常规加入）、玩家登录显式推送、
     * 以及客户端 C2S 拉取应答三处共用，保证发往客户端的是同一份全量快照。
     */
    public static DatapackSyncPacket build() {
        return new DatapackSyncPacket(
                DataPackLoader.allWeaknesses(),
                AttackerElementLoader.allMappings(),
                ItemElementActivityLoader.allMappings(),
                PhotoSetLoader.getAll(),
                PhotoSetDefs.allMap(),
                StaffItemLoader.allStaffs());
    }

    private DatapackSyncPacket(RegistryFriendlyByteBuf buf) {
        this.weaknesses = decodeWeakness(buf);
        this.attackerElement = decodeElementInt(buf);
        this.itemElementActivity = decodeElementInt(buf);
        this.photoSetMembership = decodeStringList(buf);
        this.photoSetDefs = decodeSetDefs(buf);
        int staffSize = buf.readVarInt();
        List<ResourceLocation> staffs = new ArrayList<>(staffSize);
        for (int i = 0; i < staffSize; i++) staffs.add(buf.readResourceLocation());
        this.staffItems = List.copyOf(staffs);
    }

    // ========== 编码 ==========

    private void encode(RegistryFriendlyByteBuf buf) {
        encodeWeakness(buf, weaknesses);
        encodeElementInt(buf, attackerElement);
        encodeElementInt(buf, itemElementActivity);
        encodeStringList(buf, photoSetMembership);
        encodeSetDefs(buf, photoSetDefs);
        buf.writeVarInt(staffItems.size());
        for (ResourceLocation id : staffItems) buf.writeResourceLocation(id);
    }

    private static void encodeWeakness(RegistryFriendlyByteBuf buf,
                                       Map<ResourceLocation, Map<ElementDamage, Float>> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, Map<ElementDamage, Float>> e : map.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            Map<ElementDamage, Float> inner = e.getValue();
            buf.writeVarInt(inner.size());
            for (Map.Entry<ElementDamage, Float> ie : inner.entrySet()) {
                buf.writeVarInt(ie.getKey().ordinal());
                buf.writeFloat(ie.getValue());
            }
        }
    }

    private static Map<ResourceLocation, Map<ElementDamage, Float>> decodeWeakness(RegistryFriendlyByteBuf buf) {
        int outer = buf.readVarInt();
        Map<ResourceLocation, Map<ElementDamage, Float>> map = new HashMap<>(outer);
        for (int i = 0; i < outer; i++) {
            ResourceLocation key = buf.readResourceLocation();
            int inner = buf.readVarInt();
            Map<ElementDamage, Float> m = new HashMap<>(inner);
            for (int j = 0; j < inner; j++) {
                ElementDamage el = elementOf(buf.readVarInt());
                if (el == null) { buf.readFloat(); continue; }
                m.put(el, buf.readFloat());
            }
            map.put(key, Map.copyOf(m));
        }
        return Map.copyOf(map);
    }

    private static void encodeElementInt(RegistryFriendlyByteBuf buf,
                                         Map<ResourceLocation, Map<ElementDamage, Integer>> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, Map<ElementDamage, Integer>> e : map.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            Map<ElementDamage, Integer> inner = e.getValue();
            buf.writeVarInt(inner.size());
            for (Map.Entry<ElementDamage, Integer> ie : inner.entrySet()) {
                buf.writeVarInt(ie.getKey().ordinal());
                buf.writeVarInt(ie.getValue());
            }
        }
    }

    private static Map<ResourceLocation, Map<ElementDamage, Integer>> decodeElementInt(RegistryFriendlyByteBuf buf) {
        int outer = buf.readVarInt();
        Map<ResourceLocation, Map<ElementDamage, Integer>> map = new HashMap<>(outer);
        for (int i = 0; i < outer; i++) {
            ResourceLocation key = buf.readResourceLocation();
            int inner = buf.readVarInt();
            Map<ElementDamage, Integer> m = new HashMap<>(inner);
            for (int j = 0; j < inner; j++) {
                ElementDamage el = elementOf(buf.readVarInt());
                int v = buf.readVarInt();
                if (el != null) m.put(el, v);
            }
            map.put(key, Map.copyOf(m));
        }
        return Map.copyOf(map);
    }

    private static void encodeStringList(RegistryFriendlyByteBuf buf,
                                         Map<ResourceLocation, List<String>> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<ResourceLocation, List<String>> e : map.entrySet()) {
            buf.writeResourceLocation(e.getKey());
            List<String> inner = e.getValue();
            buf.writeVarInt(inner.size());
            for (String s : inner) buf.writeUtf(s);
        }
    }

    private static Map<ResourceLocation, List<String>> decodeStringList(RegistryFriendlyByteBuf buf) {
        int outer = buf.readVarInt();
        Map<ResourceLocation, List<String>> map = new HashMap<>(outer);
        for (int i = 0; i < outer; i++) {
            ResourceLocation key = buf.readResourceLocation();
            int inner = buf.readVarInt();
            List<String> list = new ArrayList<>(inner);
            for (int j = 0; j < inner; j++) list.add(buf.readUtf());
            map.put(key, List.copyOf(list));
        }
        return Map.copyOf(map);
    }

    private static void encodeSetDefs(RegistryFriendlyByteBuf buf, Map<String, PhotoSetDefs.SetDef> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, PhotoSetDefs.SetDef> e : map.entrySet()) {
            buf.writeUtf(e.getKey());
            PhotoSetDefs.SetDef def = e.getValue();
            buf.writeUtf(def.name());
            buf.writeUtf(def.desc());
            List<PhotoSetDefs.Tier> tiers = def.tiers();
            buf.writeVarInt(tiers.size());
            for (PhotoSetDefs.Tier t : tiers) {
                buf.writeVarInt(t.count());
                List<String> effects = t.effects();
                buf.writeVarInt(effects.size());
                for (String eff : effects) buf.writeUtf(eff);
                buf.writeBoolean(t.when() != null);
                if (t.when() != null) buf.writeUtf(t.when());
            }
        }
    }

    private static Map<String, PhotoSetDefs.SetDef> decodeSetDefs(RegistryFriendlyByteBuf buf) {
        int outer = buf.readVarInt();
        Map<String, PhotoSetDefs.SetDef> map = new HashMap<>(outer);
        for (int i = 0; i < outer; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String desc = buf.readUtf();
            int tierCount = buf.readVarInt();
            List<PhotoSetDefs.Tier> tiers = new ArrayList<>(tierCount);
            for (int j = 0; j < tierCount; j++) {
                int count = buf.readVarInt();
                int effCount = buf.readVarInt();
                List<String> effects = new ArrayList<>(effCount);
                for (int k = 0; k < effCount; k++) effects.add(buf.readUtf());
                String when = buf.readBoolean() ? buf.readUtf() : null;
                tiers.add(new PhotoSetDefs.Tier(count, List.copyOf(effects), when));
            }
            map.put(id, new PhotoSetDefs.SetDef(id, name, desc, List.copyOf(tiers)));
        }
        return Map.copyOf(map);
    }

    private static ElementDamage elementOf(int ordinal) {
        ElementDamage[] values = ElementDamage.values();
        if (ordinal < 0 || ordinal >= values.length) return null;
        return values[ordinal];
    }

    @Override
    @NotNull
    public CustomPacketPayload.Type<DatapackSyncPacket> type() {
        return TYPE;
    }

    public static void handle(DatapackSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            DataPackLoader.setClientCache(packet.weaknesses);
            AttackerElementLoader.setClientCache(packet.attackerElement);
            ItemElementActivityLoader.setClientCache(packet.itemElementActivity);
            PhotoSetLoader.setClientCache(packet.photoSetMembership);
            PhotoSetDefs.setClientCache(packet.photoSetDefs);
            StaffItemLoader.setClientCache(packet.staffItems);
        });
    }
}
