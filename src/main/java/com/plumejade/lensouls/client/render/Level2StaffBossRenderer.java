package com.plumejade.lensouls.client.render;

import com.plumejade.lensouls.client.model.Level2StaffBossModel;
import com.plumejade.lensouls.entity.Level2StaffBossEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * N公司2级员工渲染器（GeckoLib）。
 */
public class Level2StaffBossRenderer extends GeoEntityRenderer<Level2StaffBossEntity> {

    public Level2StaffBossRenderer(EntityRendererProvider.Context context) {
        super(context, new Level2StaffBossModel());
    }
}
