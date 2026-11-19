package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * 照片角标：在 GUI 渲染照片物品时，于左上角叠加一个小图标。
 * <ul>
 *   <li>能力窃取照片（{@code lensouls:stolen_entity}）→ 来源生物的刷怪蛋图标（保留原版着色）；</li>
 *   <li>空间扭曲 / 时空回溯照片 → 对应能力球物品（即 {@code skill_ball/<id>.png} 那张贴图）。</li>
 * </ul>
 * <p>
 * 1.21.1 无 RegisterItemModelsEvent / ItemModel（那是 1.21.4+ 的 API），故采用 Quark 同款方案：
 * 在 ItemRenderer.render 的 TAIL 注入，仅 GUI 上下文、且物品含 lensouls:stolen_entity 时绘制叠加层。
 * 普通渲染（手持/地面/物品栏外）不受影响。做旧照片不含该标签 → 无角标。
 */
@Mixin(value = ItemRenderer.class, priority = 1001)
public abstract class ItemRendererMixin {

    private static final String STOLEN_KEY = "lensouls:stolen_entity";
    private static final String ABILITY_KEY = "lensouls:ability_type";
    // 角标缩放与位置：GUI 上下文自带正向朝向与全亮光照；BADGE_X/Y 为栏位内微调旋钮
    private static final float BADGE_SCALE = 0.6f;
    private static final double BADGE_X = -0.25;
    private static final double BADGE_Y = 0.2;
    private static boolean rendering = false;

    @Inject(method = "render", at = @At("TAIL"))
    private void lensouls$overlayPhotoBadge(ItemStack itemStackIn, ItemDisplayContext itemDisplayContext,
            boolean leftHand, PoseStack poseStackIn, MultiBufferSource bufferIn, int combinedLightIn,
            int combinedOverlayIn, BakedModel modelIn, CallbackInfo ci) {
        if (itemDisplayContext != ItemDisplayContext.GUI) return;
        CustomData cd = itemStackIn.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        CompoundTag tag = cd.copyTag();
        ItemStack badge;
        if (tag.contains(STOLEN_KEY)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(STOLEN_KEY));
            badge = PhotoBadgeIcon.getEggStack(id);
        } else {
            // 空间扭曲 / 时空回溯：角标用对应能力球贴图
            badge = PhotoBadgeIcon.getAbilityBadge(tag.getString(ABILITY_KEY));
        }
        if (badge == null || badge.isEmpty()) return;
        if (rendering) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        rendering = true;
        try {
            ItemRenderer ir = mc.getItemRenderer();
            BakedModel badgeModel = ir.getModel(badge, mc.level, null, 0);
            poseStackIn.pushPose();
            poseStackIn.translate(BADGE_X, BADGE_Y, 0.1);
            poseStackIn.scale(BADGE_SCALE, BADGE_SCALE, BADGE_SCALE);
            // GUI 上下文保证正向朝上；显式 FULL_BRIGHT(0xF000F0) 避免被环境光压暗（前置光照）
            // 关闭深度测试，使角标始终绘制在照片之上（z 在前、不被照片深度遮挡）
            RenderSystem.disableDepthTest();
            try {
                ir.render(badge, ItemDisplayContext.GUI, leftHand, poseStackIn, bufferIn,
                        0xF000F0, combinedOverlayIn, badgeModel);
            } finally {
                RenderSystem.enableDepthTest();
            }
            poseStackIn.popPose();
        } finally {
            rendering = false;
        }
    }
}
