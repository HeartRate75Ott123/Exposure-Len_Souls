package com.plumejade.lensouls.handler;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nonnull;

/**
 * 安魂曲天数计数（SavedData，镜像 InControl 的天数逻辑）。
 * <p>
 * 永远基于主世界：昼夜转换计数（夜→昼 +1），睡一觉/过一夜必然 +1；
 * 持久化到主世界 {@link DimensionDataStorage}，重进/跨存档不丢。
 * 首次创建时按主世界当前 {@code dayTime/24000} 初始化，老存档立即有数。
 */
public class FeatherAbyssDayData extends SavedData {

    private static final String NAME = "lensouls_abyss_days";

    private Boolean isDay = null;
    private int daycounter = 0;

    public FeatherAbyssDayData() {
    }

    public FeatherAbyssDayData(CompoundTag tag) {
        daycounter = tag.getInt("daycounter");
        isDay = tag.contains("isday") ? tag.getBoolean("isday") : null;
    }

    @Nonnull
    public static FeatherAbyssDayData getData(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(FeatherAbyssDayData::new,
                        (tag, provider) -> new FeatherAbyssDayData(tag)),
                NAME);
    }

    public int getDaycounter() {
        return daycounter;
    }

    /** 每 tick 推进：主世界昼夜转换计数（夜→昼 +1）；首次创建按当前天数初始化 */
    public void tick(ServerLevel overworld) {
        long time = overworld.getDayTime() % 24000L;
        boolean day = time < 12000L;
        if (isDay == null) {
            // 首次创建：老存档立即按当前世界天数初始化，之后按昼夜转换递增
            daycounter = (int) (overworld.getDayTime() / 24000L);
            isDay = day;
            setDirty();
        } else if (day != isDay) {
            if (day) {
                daycounter++;
            }
            isDay = day;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("daycounter", daycounter);
        if (isDay != null) {
            tag.putBoolean("isday", isDay);
        }
        return tag;
    }
}
