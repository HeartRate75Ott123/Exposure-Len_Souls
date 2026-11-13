package com.plumejade.lensouls.mixin.compat;

import com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 先驱者照片死亡激光：把「渲染/几何的角度来源」换成服务端的锁定角，让<b>整条原生管线</b>
 * （面片朝向、末端 endPos、命中点 collidePos、原生闪电、命中特效）都用同一个角度。
 *
 * <h2>为什么必须替换「来源值」而不是事后改结果</h2>
 * 实体 {@code tick()} 的顺序是：
 * <pre>
 *   renderYaw   = (caster.yHeadRot + 90) * π/180;      // ← 头
 *   renderPitch = -caster.getXRot() * π/180;           // ← 头
 *   ...
 *   if (tickCount &gt; 20) { calculateEndPos(); raytraceEntities(...); }
 * </pre>
 * 而客户端 {@code calculateEndPos()} 用的<b>就是 renderYaw/renderPitch</b>（服务端用同步字段
 * {@code getYaw()/getPitch()}，{@code javap} 核实）。所以只在 {@code tick()} 末尾改 renderYaw，
 * 会得到「面片按锁定角画、末端和闪电还按头部算」的错位画面 —— 实测就是「一根对不上末端的光柱
 * 外加一道旁的闪电」。
 * <p>
 * 这里改成：在我们自己的光束上，把 {@code caster.yHeadRot} / {@code caster.getXRot()} 这两个
 * <b>读取点</b>替换成「等价于锁定角的头部值」。换算与本体公式互为逆运算：
 * {@code headYaw = renderYaw*180/π − 90}、{@code xRot = −renderPitch*180/π}。
 * 于是下游全部按锁定角计算，画面与判定自然一致，渲染器的贴图/面片/闪电一行没改。
 * <p>
 * 只认「客户端 + 施法者是玩家」：本体 BOSS 自己放的激光 caster 是 BOSS 实体，原样放行。
 * 不用 {@code persistentData} 当判据 —— 它不随实体同步到客户端（踩过）。
 */
@Mixin(targets = "com.github.L_Ender.cataclysm.entity.projectile.Death_Laser_Beam_Entity", remap = false)
public abstract class CataclysmDeathLaserRenderAimMixin {

    @Redirect(method = "tick", require = 0, remap = false,
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/world/entity/LivingEntity;yHeadRot:F"))
    private float lensouls$headYawFromAim(LivingEntity caster) {
        Death_Laser_Beam_Entity self = (Death_Laser_Beam_Entity) (Object) this;
        if (!self.level().isClientSide || !(caster instanceof Player)) return caster.yHeadRot;
        return (float) (self.getYaw() * 180.0 / Math.PI - 90.0);
    }

    @Redirect(method = "tick", require = 0, remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getXRot()F"))
    private float lensouls$headPitchFromAim(LivingEntity caster) {
        Death_Laser_Beam_Entity self = (Death_Laser_Beam_Entity) (Object) this;
        if (!self.level().isClientSide || !(caster instanceof Player)) return caster.getXRot();
        return (float) (-self.getPitch() * 180.0 / Math.PI);
    }
}
