package com.plumejade.lensouls.ability;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 相机类型与能力归属（2026-11-19 用户定案）。
 * <ul>
 *   <li><b>拍立得</b>：弱点透镜 / 空间扭曲 / 能力窃取 / 时空回溯</li>
 *   <li><b>胶卷相机</b>：其余四项——时停 / 要害打击 / 夺魂索命 / 见微知著（拍立得的补集）</li>
 *   <li>两边不相交，一台相机只会看到自己那四项</li>
 * </ul>
 * 判定只按物品命名空间＋相机物品类，不硬依赖对方的类（拍立得未装时不会出错）。
 */
public final class CameraAbilityRules {

    private CameraAbilityRules() {
    }

    private static final String NS_POLAROID = "exposure_polaroid";
    private static final String NS_EXPOSURE = "exposure";

    public static boolean isInstantCamera(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && NS_POLAROID.equals(id.getNamespace());
    }

    public static boolean isFilmCamera(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !NS_EXPOSURE.equals(id.getNamespace())) return false;
        return stack.getItem() instanceof io.github.mortuusars.exposure.world.item.camera.CameraItem;
    }

    /** 拍立得专属的四项（其余四项归胶卷相机，两者互补、不相交）。 */
    public static boolean isInstantOnly(AbilityType type) {
        return type == AbilityType.WEAKNESS_LENS
                || type == AbilityType.SPATIAL_WARP
                || type == AbilityType.TEMPORAL_RECALL
                || type == AbilityType.ABILITY_STEAL;
    }

    /** 胶卷相机专属：拍立得那四项的补集。 */
    public static boolean isFilmOnly(AbilityType type) {
        return type != null && !isInstantOnly(type);
    }

    /** 这台相机能不能用这个能力；不是相机时一律放行（别把别的路径拦死）。 */
    public static boolean allows(ItemStack camera, AbilityType type) {
        if (type == null) return true;
        if (isInstantCamera(camera)) return !isFilmOnly(type);
        if (isFilmCamera(camera)) return !isInstantOnly(type);
        return true;
    }

    /** 按给定相机过滤能力列表；相机为空/不是相机时原样返回（别把菜单清空）。 */
    public static List<AbilityType> filterFor(List<AbilityType> list, ItemStack camera) {
        if (list == null || list.isEmpty()) return list;
        if (camera == null || camera.isEmpty()) return list;
        List<AbilityType> out = new ArrayList<>(list.size());
        for (AbilityType type : list) {
            if (allows(camera, type)) out.add(type);
        }
        return out.isEmpty() ? list : out;
    }
}
