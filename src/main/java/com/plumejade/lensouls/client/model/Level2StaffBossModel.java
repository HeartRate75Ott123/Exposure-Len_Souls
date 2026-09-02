package com.plumejade.lensouls.client.model;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.Level2StaffBossEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * N公司2级员工模型。
 * <p>
 * 对应资源：
 * <ul>
 *   <li>geo：{@code assets/lensouls/geo/entity/level2_staff_boss.geo.json}</li>
 *   <li>纹理：{@code assets/lensouls/textures/entity/level2_staff_boss.png}</li>
 *   <li>动画：{@code assets/lensouls/animations/entity/level2_staff_boss.animation.json}</li>
 * </ul>
 */
public class Level2StaffBossModel extends GeoModel<Level2StaffBossEntity> {

    @Override
    public ResourceLocation getModelResource(Level2StaffBossEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "geo/entity/level2_staff_boss.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(Level2StaffBossEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/entity/level2_staff_boss.png");
    }

    @Override
    public ResourceLocation getAnimationResource(Level2StaffBossEntity animatable) {
        return ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "animations/entity/level2_staff_boss.animation.json");
    }
}
