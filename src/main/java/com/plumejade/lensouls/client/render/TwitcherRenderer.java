package com.plumejade.lensouls.client.render;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.TwitcherEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 扭曲者渲染：复用僵尸模型/动画，替换贴图为 twitcher.png */
public class TwitcherRenderer extends AbstractZombieRenderer<TwitcherEntity, ZombieModel<TwitcherEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "textures/entity/twitcher.png");

    public TwitcherRenderer(EntityRendererProvider.Context context) {
        super(context,
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
    }

    @Override
    public ResourceLocation getTextureLocation(TwitcherEntity entity) {
        return TEXTURE;
    }
}
