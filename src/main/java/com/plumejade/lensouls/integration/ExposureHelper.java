package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

/**
 * Exposure 照片数据读取辅助类。
 * <p>
 * 通过序列化 ItemStack → NBT 读取实体 ID，无需编译期依赖 Exposure。
 */
public class ExposureHelper {

    private static final Logger LOGGER = LenSouls.LOGGER;
    private static final ResourceLocation PHOTO_ITEM_ID =
            ResourceLocation.parse("exposure:photograph");
    /** 玩家实体类型 id —— 「主体不认玩家」的共用地基，理由见 {@link #isPlayerEntityId} */
    private static final ResourceLocation PLAYER_ENTITY_ID =
            ResourceLocation.fromNamespaceAndPath("minecraft", "player");

    /**
     * 该实体 id 是否玩家。
     * <p>
     * <b>用途</b>：凡是「把主体记录进照片」的链路都<b>不认玩家</b>——
     * 能力窃取（玩家没有实体照片效果条目，选中它整张照片退化成普通照片）与
     * 弱点透镜（用户口径：弱点透镜不需要帧内有其他玩家）。
     * 帧列表里那些"拍队友"的玩法（滤镜 / 药水玻璃板）<b>不经过本判定</b>：
     * 它们直接读 {@code FrameAddedEvent.getEntitiesInFrame()}，玩家照旧在列表里。
     * <p>
     * 另：相机持有者本身已由 {@code FrameEntities.assemble} 在源头排除；
     * 这里还要再判一次，是因为<b>旧照片</b>（1.5.50 之前拍的）存的
     * {@code entities_in_frame} 里仍留着拍摄者自己，而 {@code EntityInFrame} 只存
     * {@code (id,name,pos,distance)}、<b>没有 UUID</b>，无法精确定位"哪一条是拍摄者"，
     * 只能按"玩家一律不算主体"处理。
     */
    public static boolean isPlayerEntityId(ResourceLocation id) {
        return PLAYER_ENTITY_ID.equals(id);
    }

    /**
     * 判断物品是否为 Exposure 照片（通过物品 ID 比对）。
     */
    public static boolean isExposurePhotograph(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return PHOTO_ITEM_ID.equals(itemId);
    }

    /**
     * 判断照片是否含有摄魂术标记（由 PhotoInjectionHandler 注入 {@code lensouls:injected}）。
     * 无此标记的照片为普通相机未经附魔所拍，不参与任何摄魂术流程。
     */
    public static boolean hasSoulData(ItemStack stack) {
        if (!isExposurePhotograph(stack)) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        return tag.getBoolean("lensouls:injected");
    }

    /**
     * 判断照片是否可用于剑槽（仅弱点透镜照片可装剑触发增伤）。
     * 空间扭曲、时空回溯等其他能力的照片虽然也有 {@code lensouls:injected} 标记，
     * 但 {@code lensouls:ability_type} 不同，不会被剑槽接受，防止数据流混淆。
     */
    public static boolean isSwordSlotSuitable(ItemStack stack) {
        if (!hasSoulData(stack)) return false;
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        String abilityType = tag.getString("lensouls:ability_type");
        // 无 ability_type（旧版兼容）或 weakness_lens 的才可装剑
        return abilityType.isEmpty() || "weakness_lens".equals(abilityType);
    }

    /**
     * 从 Exposure 照片中读取第一个实体的类型 ID。
     * 将 ItemStack 序列化为 NBT 后解析组件数据。
     *
     * @return 实体注册名（如 {@code minecraft:zombie}），失败返回 null
     */
    public static ResourceLocation getEntityId(ItemStack photoStack, RegistryAccess access) {
        if (!isExposurePhotograph(photoStack)) return null;

        try {
            // 序列化整个 ItemStack 到 NBT（包含 DataComponent 数据）
            CompoundTag tag = (CompoundTag) photoStack.save(access, new CompoundTag());

            // DataComponent 存储在 "components" 字段
            CompoundTag components = tag.getCompound("components");
            if (components.isEmpty()) return null;

            // 遍历所有组件键，找到 exposure:photograph_frame
            for (String key : components.getAllKeys()) {
                if (key.contains("photograph_frame")) {
                    CompoundTag frameTag = components.getCompound(key);
                    return parseFirstEntityId(frameTag);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static ResourceLocation parseFirstEntityId(CompoundTag frameTag) {
        // Frame.entitiesInFrame 字段
        if (frameTag.contains("entities_in_frame", Tag.TAG_LIST)) {
            ListTag entities = frameTag.getList("entities_in_frame", Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                String idStr = entities.getCompound(i).getString("id");
                if (idStr.isEmpty()) continue;
                ResourceLocation id;
                try {
                    id = ResourceLocation.parse(idStr);
                } catch (Exception ignored) {
                    continue;   // 坏 id 就往后找一个能解析的
                }
                // 玩家不算主体：走本方法的只有弱点透镜那几条链路（见 isPlayerEntityId）；
                // 旧照片里可能还留着拍摄者自己，必须跳过
                if (isPlayerEntityId(id)) continue;
                return id;
            }
        }
        return null;
    }
}
