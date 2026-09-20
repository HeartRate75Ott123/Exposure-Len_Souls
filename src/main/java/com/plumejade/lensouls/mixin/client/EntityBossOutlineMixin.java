package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.StatusGlintBufferSource;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 实体描边接管（Apotheosis 同款注入点，直接扳原版描边管线开关，不经药水效果）：
 * <ul>
 *   <li>{@code isCurrentlyGlowing}：是否描边（原版据此把实体画进 OutlineBufferSource）；</li>
 *   <li>{@code getTeamColor}：描边颜色（无队伍时原版为白色）。</li>
 * </ul>
 * 开关与颜色均由状态数据驱动（{@link StatusGlintBufferSource#resolveState}，
 * 查韧性客户端缓存——服务端每 tick 同步，优先级 破定 > 定格 > 无敌）：
 * <ol>
 *   <li><b>破定（STUNNED）</b>：红色描边，与定身红 glint 一致；</li>
 *   <li><b>白霸体（INVINCIBLE）</b>：白色描边，与白 glint 一致；</li>
 *   <li><b>定格（FROZEN）</b>：不接管，蓝描边走 FrozenOutlineManager 既有管线；</li>
 *   <li><b>镜魂 DoT</b>：元素主色描边（玩家第三人称全身描边，原逻辑）。</li>
 * </ol>
 * 状态消失描边自动消失（开关即状态本身），无清理面。
 */
@Mixin(Entity.class)
public abstract class EntityBossOutlineMixin {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void lensouls$bossGlow(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity le = lensouls$outlineOwner((Entity) (Object) this);
        if (le != null && !lensouls$freezeTakesOver(le)) {
            StatusGlintBufferSource.State state = StatusGlintBufferSource.resolveState(le);
            if (state == StatusGlintBufferSource.State.STUNNED
                    || state == StatusGlintBufferSource.State.INVINCIBLE) {
                cir.setReturnValue(true);
                return;
            }
            if (BossOutlineColors.fromEntity(le) != null) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true)
    private void lensouls$bossTeamColor(CallbackInfoReturnable<Integer> cir) {
        LivingEntity le = lensouls$outlineOwner((Entity) (Object) this);
        if (le != null && !lensouls$freezeTakesOver(le)) {
            StatusGlintBufferSource.State state = StatusGlintBufferSource.resolveState(le);
            if (state == StatusGlintBufferSource.State.STUNNED) {
                // 与原版 ChatFormatting.RED 色值一致
                cir.setReturnValue(0xFF5555);
                return;
            }
            if (state == StatusGlintBufferSource.State.INVINCIBLE) {
                cir.setReturnValue(0xFFFFFF);
                return;
            }
            BossOutlineColors colors = BossOutlineColors.fromEntity(le);
            if (colors != null) {
                cir.setReturnValue(colors.primaryColor());
            }
        }
    }

    /**
     * 时间定格（蓝色自定义描边）接管期间，白/红描边必须<b>静默</b>，定格结束后自然恢复。
     * <p>
     * 蓝色描边走的是另一条完全独立的管线：{@code StatusGlintBufferSource} 把该实体的模型渲染改道进
     * mask FBO（见其 {@code getBuffer} 里的 {@code MaskRenderTypes.maskTypeForEntity}），再由
     * {@code FrozenOutlineManager} 帧末合成蓝边。它<b>不</b>依赖本 mixin 接管的
     * {@code isCurrentlyGlowing}/{@code getTeamColor}，所以这里直接不接管是安全的。
     * <p>
     * 若不管：定格中的破定 boss 会被 {@code resolveState} 判成 STUNNED（破定 > 定格）→ 既画蓝边又画红边，
     * 变成红蓝混色；白霸体同理。这里用「冻结标记本身」做闸门（而不是 resolveState 的结果），
     * 因此在冻结期间无论同时还挂着破定/霸体都一律静默，冻结标记消失后立即恢复原优先级。
     */
    private static boolean lensouls$freezeTakesOver(LivingEntity owner) {
        return com.plumejade.lensouls.ability.client.ClientFreezeCache.isFrozen(owner.getId());
    }

    /**
     * 解析描边状态与颜色的主体实体：<b>多子部件 boss 的部件取父实体</b>。
     * <p>
     * 暮色森林的九头蛇头（{@code HydraHead}）/娜迦体节（{@code NagaSegment}）与末影龙部件一样，
     * 都是 NeoForge 的 {@link PartEntity} 子实体，各自独立走一次 {@code Entity} 的描边开关与颜色查询。
     * 它们是 {@code Entity} 而<b>不是</b> {@code LivingEntity}：只按本体（LivingEntity）解析状态时，
     * 部件这一支拿不到破定/霸体状态，颜色就落回原版队伍色（白），于是出现「三个头白边、身子红边」，
     * 娜迦则是「头红、体节白」。
     * <p>
     * 这里统一改为：部件一律用 {@link PartEntity#getParent()} 的父实体去查状态与配色，
     * 保证同一只 boss 的所有部件与本体描边同色同灭。
     */
    private static LivingEntity lensouls$outlineOwner(Entity self) {
        Entity owner = self;
        if (self instanceof PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent != null) owner = parent;
        }
        return owner instanceof LivingEntity le ? le : null;
    }
}
