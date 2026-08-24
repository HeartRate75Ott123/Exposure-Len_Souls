package com.plumejade.lensouls.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.plumejade.lensouls.ability.client.BossOutlineBufferSource;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.CaptureState;
import com.plumejade.lensouls.ability.client.ClientFreezeCache;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import com.plumejade.lensouls.ability.client.ItemRenderTracker;
import com.plumejade.lensouls.ability.client.StatusGlintBufferSource;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import com.plumejade.lensouls.client.phantom.ClientPhantomHandler;
import com.plumejade.lensouls.client.phantom.PhantomBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.entity.PartEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 状态光效统一注入点（顶点双写方案核心）。
 * <p>
 * 替换旧 {@code FrozenEntityRenderMixin}：注入点从 {@code LivingEntityRenderer}
 * 上移到 {@code EntityRenderDispatcher}，覆盖 GeckoLib、多部件 BOSS
 * （末影龙部件、暮色九头蛇头/颈）等所有经 dispatcher 渲染的实体。
 * <p>
 * 生命周期：
 * <ul>
 *   <li>HEAD：定格实体 → 清 mask FBO + {@link CaptureState#tryStartCapture}（按帧去重）；</li>
 *   <li>@Redirect：替换 {@code EntityRenderer.render} 的 buffer 参数为
 *       {@link StatusGlintBufferSource}（手部物品渲染除外）；</li>
 *   <li>RETURN：提交 mask 顶点（描边）并结束捕获。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String RENDER_METHOD =
            "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    private static final String RENDERER_RENDER =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    /** 多部件实体（末影龙部件、暮色九头蛇头/颈等）追溯到父实体查询状态。 */
    private static Entity resolveRoot(Entity entity) {
        while (entity instanceof PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent == null) break;
            entity = parent;
        }
        return entity;
    }

    @Inject(method = RENDER_METHOD, at = @At("HEAD"))
    private void lensouls$beginCapture(Entity entity, double x, double y, double z,
                                       float rotationYaw, float partialTicks,
                                       PoseStack poseStack, MultiBufferSource buffer,
                                       int packedLight, CallbackInfo ci) {
        Entity root = resolveRoot(entity);
        // 纯冻结判定（不经 STUNNED 优先级）：冻结中破韧实体也画蓝描边
        if (!ClientFreezeCache.isFrozen(root.getId())) {
            return;
        }
        // 只在主渲染 pass 捕获（Iris 阴影 pass 在 AFTER_SKY 之前渲染实体，
        // 若捕获会把阴影投影写入 mask，描边错位；原版 outline 同样只在主 pass 生效）
        if (!CaptureState.isMainPassActive()) {
            return;
        }
        FrozenOutlineManager.ensureMaskCleared();
        boolean captured = CaptureState.tryStartCapture(root.getId());
        if (captured) {
            LOGGER.info("[FrozenMask] render entity={} (id={}) state=FROZEN captured=true",
                    root.getName().getString(), root.getId());
        }
    }

    @SuppressWarnings("rawtypes")
    @Redirect(method = RENDER_METHOD, at = @At(value = "INVOKE", target = RENDERER_RENDER))
    private void lensouls$redirectRender(EntityRenderer renderer, Entity entity,
                                         float rotationYaw, float partialTicks,
                                         PoseStack poseStack, MultiBufferSource buffer,
                                         int packedLight) {
        Entity root = resolveRoot(entity);
        if (ItemRenderTracker.isRenderingItem()) {
            renderer.render(entity, rotationYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        StatusGlintBufferSource.State state = StatusGlintBufferSource.resolveState(root);
        if (state == StatusGlintBufferSource.State.NONE) {
            // 虚灵：统一半透明（原色 + 0.5 alpha），覆盖多部件/GeckoLib
            if (ClientPhantomHandler.isPhantomEntity(root.getId())) {
                renderer.render(entity, rotationYaw, partialTicks, poseStack,
                        new PhantomBufferSource(buffer), packedLight);
                return;
            }
            // 玩家 BOSS 镜魂：描边（dispatcher 层双写 mask，与时间定格同构）
            if (entity instanceof Player player
                    && Minecraft.getInstance().screen == null) {
                BossOutlineColors colors = BossOutlineColors.fromEntity(player);
                if (colors != null && BossOutlineManager.tryStartCapture(player.getId())) {
                    BossOutlineManager.setColors(colors);
                    renderer.render(entity, rotationYaw, partialTicks, poseStack,
                            new BossOutlineBufferSource(buffer), packedLight);
                    return;
                }
            }
            // 玩家/普通实体：照常渲染（彩色）
            renderer.render(entity, rotationYaw, partialTicks, poseStack, buffer, packedLight);
            return;
        }
        renderer.render(entity, rotationYaw, partialTicks, poseStack,
                new StatusGlintBufferSource(buffer, state,
                        ClientFreezeCache.isFrozen(root.getId())),
                packedLight);
    }

    @Inject(method = RENDER_METHOD, at = @At("RETURN"))
    private void lensouls$endCapture(Entity entity, double x, double y, double z,
                                     float rotationYaw, float partialTicks,
                                     PoseStack poseStack, MultiBufferSource buffer,
                                     int packedLight, CallbackInfo ci) {
        // mask 顶点已按 per-纹理类型写入（层切换即 flush，每层绑自己纹理），
        // 这里兜底 flush 最后一层（含冻结 + BOSS 描边收集的顶点）
        CaptureState.flushMask();
        CaptureState.endCapture();
        if (BossOutlineManager.isCapturing()) {
            BossOutlineManager.endCapture();
        }
    }
}
