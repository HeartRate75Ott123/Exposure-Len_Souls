package com.plumejade.lensouls.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.entity.EntityType;

/**
 * 实体照片饰品渲染：在 GUI 内于左上角叠加来源生物对应的小刷怪蛋角标。
 * <p>
 * 底图走物品自身 model（照片框），避免递归进入本自定义渲染器；
 * 来源生物取自 lensouls:stolen_entity，通过 SpawnEggItem.byId 取得对应刷怪蛋，
 * 无刷怪蛋的生物用原版基础蛋（白蛋）兜底。仅 GUI 上下文叠加角标。
 */
public class EntityPhotoEggRenderer extends BlockEntityWithoutLevelRenderer {

    public EntityPhotoEggRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();

        // 底图：照片框（用物品自身 model 直接渲染，绕过自定义渲染器递归）
        BakedModel baseModel = mc.getItemRenderer().getItemModelShaper().getItemModel(stack.getItem());
        itemRenderer.render(stack, context, context == ItemDisplayContext.GUI,
                poseStack, bufferSource, packedLight, packedOverlay, baseModel);

        // 仅 GUI 叠加来源生物小刷怪蛋角标
        if (context != ItemDisplayContext.GUI) return;

        ResourceLocation entityId = readStolenEntity(stack);
        if (entityId == null) return;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entityId);
        if (type == null || type == EntityType.PLAYER) return;

        SpawnEggItem eggItem = SpawnEggItem.byId(type);
        ItemStack eggStack = eggItem != null
                ? new ItemStack(eggItem)
                : new ItemStack(Items.PIG_SPAWN_EGG); // 无刷怪蛋：原版基础蛋兜底

        BakedModel eggModel = mc.getItemRenderer().getItemModelShaper().getItemModel(eggStack.getItem());
        poseStack.pushPose();
        // 左上角 1/4 尺寸（模型空间原点居中，负方向为左上）
        poseStack.translate(-4.0, -4.0, 0.0);
        poseStack.scale(0.5f, 0.5f, 0.5f);
        itemRenderer.render(eggStack, ItemDisplayContext.GUI, true,
                poseStack, bufferSource, packedLight, packedOverlay, eggModel);
        poseStack.popPose();
    }

    private static ResourceLocation readStolenEntity(ItemStack stack) {
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        var tag = customData.getUnsafe();
        if (!tag.contains("lensouls:stolen_entity")) return null;
        return ResourceLocation.parse(tag.getString("lensouls:stolen_entity"));
    }
}
