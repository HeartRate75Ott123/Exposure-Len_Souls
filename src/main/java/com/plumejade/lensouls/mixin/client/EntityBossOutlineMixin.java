package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.ability.client.StatusGlintBufferSource;
import com.plumejade.lensouls.client.outline.BossOutlineColors;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
        if ((Object) this instanceof LivingEntity le) {
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
        if ((Object) this instanceof LivingEntity le) {
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
}
