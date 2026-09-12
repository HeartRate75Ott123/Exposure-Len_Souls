package com.plumejade.lensouls.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.plumejade.lensouls.entity.PhantomRemnantEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * 虚影残像渲染器——{@code PermanentItemRenderer}（Enigmatic Legacy）的 1:1 移植。
 * <p>
 * EL 的容器是自定义 {@code PermanentItemEntity extends Entity}，因此它自带了 default
 * ItemRenderer 的一份拷贝；本类同样是<b>自己用 {@link ItemRenderer} 画物品模型</b>，
 * 而不是继承 {@link net.minecraft.client.renderer.entity.ItemEntityRenderer}
 * ——这样缩放/位移完全由我们掌控，不受原版渲染器内部变换影响。
 * <p>
 * 逐项对应 EL 的实现：
 * <ul>
 *   <li>「永久水晶」类物品：放大（{@link #SCALE}）。</li>
 *   <li>阴影：{@code shadowRadius 0.15 / shadowStrength 0.75}（EL 原值）。</li>
 *   <li>浮动：{@code sin((age + partial) / 10 + hoverStart) * 0.1 + 0.1}；
 *       EL 的 {@code hoverStart} 对应原版 {@code ItemEntity.bobOffs}（同为 random·2π）。
 *       相位源用 {@code tickCount}（见 {@link #render} 内注释：本实体的 {@code age} 被
 *       {@code setUnlimitedLifetime()} 冻结在 -32768）。</li>
 *   <li>自转：EL 的 {@code entity.getItemHover(partial)} 对应原版 {@code ItemEntity.getSpin(partial)}，
 *       同式换成 {@code tickCount} 驱动。</li>
 *   <li>多枚堆叠时的散布与推进、非 GUI3D 模型沿 Z 的前后错位，均按 EL 原逻辑保留。</li>
 *   <li>贴图定位：{@link TextureAtlas#LOCATION_BLOCKS}。</li>
 *   <li>观察者已死亡且距容器 ≤1 格时不渲染（EL 同款细节）。</li>
 * </ul>
 * 调整大小只改 {@link #SCALE}。
 */
public class PhantomRemnantRenderer extends EntityRenderer<PhantomRemnantEntity> {

    /**
     * 放大倍数。
     * <p>
     * 注意物品模型走 {@link ItemDisplayContext#GROUND}，而 {@code item/generated} 的 ground
     * 变换自带 0.25 缩放，所以最终观感 ≈ {@code SCALE × 0.25} 个方块（原版掉落物 = 0.25）。
     * 即 2.0 表示「约为普通掉落物的 2 倍」；EL 原版容器为 1.25（约 1.25 倍）。
     */
    public static final float SCALE = 2.0F;

    private final ItemRenderer itemRenderer;
    private final Random random = new Random();

    public PhantomRemnantRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    /** EL 同款：按堆叠数量决定要画几份（本模组物品恒为 1） */
    private static int getModelCount(ItemStack stack) {
        if (stack.getCount() > 48) return 5;
        if (stack.getCount() > 32) return 4;
        if (stack.getCount() > 16) return 3;
        if (stack.getCount() > 1) return 2;
        return 1;
    }

    @Override
    public void render(PhantomRemnantEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        var player = Minecraft.getInstance().player;
        // EL 细节：观察者已死亡且贴得很近时不渲染（死亡视角里不出现容器糊脸）
        if (player != null && !player.isAlive()
                && entity.distanceToSqr(player.getX(), player.getEyeY(), player.getZ()) <= 1.0) {
            return;
        }

        poseStack.pushPose();
        ItemStack stack = entity.getItem();

        int seed = stack.isEmpty() ? 187 : Item.getId(stack.getItem()) + stack.getDamageValue();
        this.random.setSeed(seed);

        BakedModel model = this.itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        boolean gui3d = model.isGui3d();
        int count = getModelCount(stack);

        // ⚠ 动画相位必须用 tickCount，绝不能用 getAge()：
        //   PhantomRemnantEntity 为了「永不消失」调用了 ItemEntity.setUnlimitedLifetime()，
        //   而它的字节码实现就是 `this.age = -32768`（原版「无限寿命」哨兵值），tick() 里又有
        //       if (this.age != -32768) { ++this.age; }
        //   → age 被永久冻结在 -32768。而 getSpin() 的公式是 (age + partial) / 20 + bobOffs，
        //   冻结后每 tick 只在 partialTick 的 0→1 区间里变化、下一 tick 又回落：
        //   视觉症状就是「定死一个方向 + 抽搐」（上下浮动同理变成锯齿抖动）。
        //   tickCount 由 Entity.baseTick() 每 tick 自增、不受哨兵值影响，且两端各自都在走，
        //   本项目 BossPhantomRenderer 的动画时钟也是这么用的。
        float anim = entity.tickCount + partialTick;
        float bob = Mth.sin(anim / 10.0F + entity.bobOffs) * 0.1F + 0.1F;
        float groundScaleY = model.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
        poseStack.translate(0.0, bob + 0.25F * groundScaleY, 0.0);

        // 自转：与原版 ItemEntity.getSpin(partialTick) 完全同式，只把被冻结的 age 换成 tickCount
        poseStack.mulPose(Axis.YP.rotation(anim / 20.0F + entity.bobOffs));

        if (!gui3d) {
            // 平面模型沿 Z 前后错开（EL 原逻辑，去掉其中恒为 0 的 x/y 项）
            poseStack.translate(0.0, 0.0, -0.09375F * (count - 1) * 0.5F);
        }

        for (int k = 0; k < count; k++) {
            poseStack.pushPose();
            if (k > 0) {
                if (gui3d) {
                    float dx = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float dy = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float dz = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    poseStack.translate(dx, dy, dz);
                } else {
                    float dx = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    float dy = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    poseStack.translate(dx, dy, 0.0D);
                }
            }

            // 放大只作用于模型本身：位置（浮动/自转/散布）已在上面算好且不受缩放影响，
            // 否则 EL 那种「先放大再做位移」的写法会把浮动幅度也乘上 SCALE——
            // 小倍数（EL 用 1.25）看不出来，5 倍时会把物品抬到 1.5 格外并剧烈上下摆。
            poseStack.pushPose();
            poseStack.scale(SCALE, SCALE, SCALE);

            this.itemRenderer.render(stack, ItemDisplayContext.GROUND, false, poseStack, bufferSource,
                    packedLight, OverlayTexture.NO_OVERLAY, model);

            poseStack.popPose();
            poseStack.popPose();
            if (!gui3d) {
                poseStack.translate(0.0, 0.0, 0.09375F);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PhantomRemnantEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
