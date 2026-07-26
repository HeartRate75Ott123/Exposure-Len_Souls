package com.plumejade.lensouls.ability.util;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 时空回溯快照数据结构。
 * <p>
 * 记录拍照时的维度、坐标、视角、生命值、饥饿值、饱和度。
 */
public class TemporalSnapshot {

    private static final String TAG_DIMENSION = "dimension";
    private static final String TAG_POS_X = "pos_x";
    private static final String TAG_POS_Y = "pos_y";
    private static final String TAG_POS_Z = "pos_z";
    private static final String TAG_YAW = "yaw";
    private static final String TAG_PITCH = "pitch";
    private static final String TAG_HEALTH = "health";
    private static final String TAG_FOOD = "food";
    private static final String TAG_SATURATION = "saturation";

    private final ResourceLocation dimension;
    private final Vec3 pos;
    private final float yaw;
    private final float pitch;
    private final float health;
    private final int foodLevel;
    private final float saturation;

    public TemporalSnapshot(ResourceLocation dimension, Vec3 pos, float yaw, float pitch,
                            float health, int foodLevel, float saturation) {
        this.dimension = dimension;
        this.pos = pos;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
    }

    // ========== 拍照时捕获 ==========

    /** 从玩家当前状态创建快照 */
    public static TemporalSnapshot capture(ServerPlayer player) {
        TemporalSnapshot snap = new TemporalSnapshot(
                player.level().dimension().location(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.getHealth(),
                player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel()
        );
        return snap;
    }

    // ========== 回溯执行 ==========

    /** 将快照应用到玩家身上（跨维度传送+状态恢复） */
    public void apply(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // 获取目标维度
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension);
        ServerLevel targetLevel = server.getLevel(dimKey);
        if (targetLevel == null) {
            LenSouls.LOGGER.warn("[TemporalSnapshot] 无法找到维度: {}", dimension);
            return;
        }

        // 强制加载目标区块
        targetLevel.getChunk((int) Math.floor(pos.x) >> 4, (int) Math.floor(pos.z) >> 4);


        // 传送
        player.teleportTo(targetLevel, pos.x, pos.y, pos.z, yaw, pitch);

        // 恢复状态
        player.setHealth(health);
        player.getFoodData().setFoodLevel(foodLevel);
        player.getFoodData().setSaturation(saturation);

    }

    // ========== NBT 序列化 ==========

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_DIMENSION, dimension.toString());
        tag.putDouble(TAG_POS_X, pos.x);
        tag.putDouble(TAG_POS_Y, pos.y);
        tag.putDouble(TAG_POS_Z, pos.z);
        tag.putFloat(TAG_YAW, yaw);
        tag.putFloat(TAG_PITCH, pitch);
        tag.putFloat(TAG_HEALTH, health);
        tag.putInt(TAG_FOOD, foodLevel);
        tag.putFloat(TAG_SATURATION, saturation);
        return tag;
    }

    public static TemporalSnapshot fromTag(CompoundTag tag) {
        ResourceLocation dim = ResourceLocation.parse(tag.getString(TAG_DIMENSION));
        Vec3 pos = new Vec3(tag.getDouble(TAG_POS_X), tag.getDouble(TAG_POS_Y), tag.getDouble(TAG_POS_Z));
        float yaw = tag.getFloat(TAG_YAW);
        float pitch = tag.getFloat(TAG_PITCH);
        float health = tag.getFloat(TAG_HEALTH);
        int food = tag.getInt(TAG_FOOD);
        float sat = tag.getFloat(TAG_SATURATION);
        return new TemporalSnapshot(dim, pos, yaw, pitch, health, food, sat);
    }

    // ========== 照片交互 ==========

    private static final String PHOTO_KEY = "lensouls:ability_data";
    private static final String SNAPSHOT_KEY = "temporal_snapshot";

    /** 将快照写入照片 CustomData */
    public static void writeToPhoto(ItemStack photo, TemporalSnapshot snapshot) {
        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.put("lensouls:snapshot", snapshot.toTag());
        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 从照片 CustomData 读取快照，没有则返回 null */
    public static TemporalSnapshot fromPhoto(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        if (tag.contains("lensouls:snapshot", Tag.TAG_COMPOUND)) {
            return fromTag(tag.getCompound("lensouls:snapshot"));
        }
        return null;
    }

    /** 判断物品是否为时空回溯照片 */
    public static boolean hasSnapshot(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains("lensouls:snapshot", Tag.TAG_COMPOUND);
    }
}
