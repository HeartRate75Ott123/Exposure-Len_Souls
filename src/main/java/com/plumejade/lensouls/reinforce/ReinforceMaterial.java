package com.plumejade.lensouls.reinforce;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 一种强化材料的数据定义（数据包 {@code data/lensouls/reinforcement/*.json} 的 materials 项）。
 *
 * @param id        材料物品 ID
 * @param modifiers 该材料给予被强化物品的属性修饰符（可多条）
 * @param desc      额外 tooltip 行（原样文本，可留空）
 */
public record ReinforceMaterial(ResourceLocation id, List<ReinforceModifier> modifiers, List<String> desc) {

    // ========== 网络序列化（随 DatapackSyncPacket 整体下发到客户端） ==========

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeVarInt(modifiers.size());
        for (ReinforceModifier modifier : modifiers) modifier.encode(buf);
        buf.writeVarInt(desc.size());
        for (String line : desc) buf.writeUtf(line);
    }

    public static ReinforceMaterial decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int modifierCount = buf.readVarInt();
        List<ReinforceModifier> modifiers = new ArrayList<>(modifierCount);
        for (int i = 0; i < modifierCount; i++) {
            ReinforceModifier modifier = ReinforceModifier.decode(buf);
            if (modifier != null) modifiers.add(modifier);
        }
        int descCount = buf.readVarInt();
        List<String> desc = new ArrayList<>(descCount);
        for (int i = 0; i < descCount; i++) desc.add(buf.readUtf());
        return new ReinforceMaterial(id, List.copyOf(modifiers), List.copyOf(desc));
    }

    public static void encodeAll(FriendlyByteBuf buf, Collection<ReinforceMaterial> materials) {
        buf.writeVarInt(materials.size());
        for (ReinforceMaterial material : materials) material.encode(buf);
    }

    public static List<ReinforceMaterial> decodeAll(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ReinforceMaterial> materials = new ArrayList<>(size);
        for (int i = 0; i < size; i++) materials.add(decode(buf));
        return List.copyOf(materials);
    }
}
