package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plumejade.lensouls.client.PhotoBadgeIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 照片角标：在 GUI 渲染照片物品时，于左上角叠加来源生物的刷怪蛋图标（保留原版着色）。
 * <p>
 * 1.21.1 无 RegisterItemModelsEvent / ItemModel（那是 1.21.4+ 的 API），故采用 Quark 同款方案：
 * 在 ItemRenderer.render 的 TAIL 注入，仅 GUI 上下文、且物品含 lensouls:stolen_entity 时绘制叠加层。
 * 普通渲染（手持/地面/物品栏外）不受影响。做旧照片不含该标签 → 无角标。
 */
@Mixin(value = ItemRenderer.class, priority = 1001)
public abstract class ItemRendererMixin {

    private static final String STOLEN_KEY = "lensouls:stolen_entity";
    // 角标在 GUI 物品模型空间（约 16 单位）的偏移与缩放，需 runClient 实测微调
    private static final float BADGE_SCALE = 0.4f;
    private static final double BADGE_X = 0.5;
    private static final double BADGE_Y = 0.5;
    private static boolean rendering = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void lensouls$overlayPhotoBadge(ItemStack itemStackIn, ItemDisplayContext itemDisplayContext,
            boolean leftHand, PoseStack poseStackIn, MultiBufferSource bufferIn, int combinedLightIn,
            int combinedOverlayIn, BakedModel modelIn, CallbackInfo ci) {
        if (itemDisplayContext != ItemDisplayContext.GUI) return;
        CustomData cd = itemStackIn.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        CompoundTag tag = cd.copyTag();
        if (!tag.contains(STOLEN_KEY)) return;

        ResourceLocation id = ResourceLocation.tryParse(tag.getString(STOLEN_KEY));
        ItemStack egg = PhotoBadgeIcon.getEggStack(id);
        if (egg == null || egg.isEmpty()) return;
        if (rendering) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        rendering = true;
        try {
            ItemRenderer ir = mc.getItemRenderer();
            BakedModel eggModel = ir.getModel(egg, mc.level, null, 0);
            poseStackIn.pushPose();
            poseStackIn.translate(BADGE_X, BADGE_Y, 0);
            poseStackIn.scale(BADGE_SCALE, BADGE_SCALE, BADGE_SCALE);
            ir.render(egg, ItemDisplayContext.GUI, leftHand, poseStackIn, bufferIn, combinedLightIn, combinedOverlayIn, eggModel);
            poseStackIn.popPose();
        } finally {
            rendering = false;
        }
    }
}
