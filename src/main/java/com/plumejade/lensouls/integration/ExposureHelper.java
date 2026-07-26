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
            if (!entities.isEmpty()) {
                String idStr = entities.getCompound(0).getString("id");
                if (!idStr.isEmpty()) {
                    return ResourceLocation.parse(idStr);
                }
            }
        }
        return null;
    }
}
