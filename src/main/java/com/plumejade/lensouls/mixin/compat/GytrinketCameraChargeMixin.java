package com.plumejade.lensouls.mixin.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * gytrinket「充能攻击」兼容：手持相机时不让它进入充能，以免干扰拍照。
 * <p>
 * <b>为什么要掐这两处</b>（对着该模组源码逐点核实）：
 * <ul>
 *   <li>它的门槛谓词 {@code AttackModeClientUtil.hasChargedAttackItem()} 查的是
 *       <b>光点核心存储（{@code PlayerStore}，27 槽）+ Curios 饰品栏</b>里有没有装着对应模块
 *       ——<b>与主手拿什么完全无关</b>，所以「手持相机时前置条件天然不成立」并不成立：装了模块的玩家
 *       手持相机照样被卷进去；</li>
 *   <li>于是 {@code MouseHandlerMixin} 会在<b>任何一次右键按下</b>时调用
 *       {@code startChargingFromRightButton()} → 置 {@code isRightCharging} 并向服务端发
 *       {@code ItemUseChargePayload}。Exposure 相机正是「长按右键开取景器」，这一下就被卷进充能流程：
 *       服务端会给玩家挂上它自己的 <b>-3.0 攻速</b>修饰符（相机不在它的武器白名单里，
 *       必然取默认值），并每 tick 抛 {@code ChargedAttackEvent}；</li>
 *   <li>左键侧：{@code startCharging()} + 松开时的 {@code releaseAttack()} 会做
 *       {@code findTargetInCrosshair} 的矩形光束索敌，找到就 {@code gameMode.attack(...)}
 *       —— 也就是<b>「对着空气点一下切能力」会额外打出一记真实近战</b>。</li>
 * </ul>
 * 因此两个充能入口都掐掉：不进充能 ⇒ 不发那两个包 ⇒ 服务端永不进入充能 ⇒
 * ① 没有释放时的幽灵索敌近战、② 右键取景器期间没有 -3.0 攻速与 CHARGING 事件、
 * ③ {@code continueAttack} 不再被它 cancel。其余情况下它的充能系统完全不受影响。
 * <p>
 * <b>已知残留（刻意保留）</b>：它的 {@code MinecraftClientMixin.startAttack} 里
 * {@code hasChargedAttackItem()} 分支判断在前、调 {@code startCharging()} 在后，我们只掐掉后者，
 * 所以相机在手时 {@code startAttack} 仍会 {@code setReturnValue(false)} ——
 * 表现为<b>「拿相机点左键没有原版挥击动作/动画」</b>。
 * 这不影响我们的照片弹幕：{@code lensouls} 自己的 {@code MinecraftSwingMixin}（priority 1000）
 * 在 HEAD 处的执行顺序<b>早于</b>它（999 后应用者才插到最前，详见下方说明），{@code PhotoSwingPacket} 照发。
 * 若哪天要去掉这处残留，正确做法是覆写它的门槛谓词
 * {@code AttackModeClientUtil.hasChargedAttackItem()} / {@code hasAssaultItem()}（相机在手时返回 false），
 * 而不是去 hook 它的 mixin 类或 {@code isCharging()}。
 * <p>
 * <b>Mixin 执行顺序备忘</b>（别想当然）：{@code setReturnValue} 内部会 {@code cancel()}，
 * 同注入点上<b>更靠后的回调会被跳过</b>，所以「同点多个 @Inject 一定都会跑」是错的。
 * 我们之所以没被它掐掉，是因为优先级数字小者<b>先应用</b>、先应用者插在下面，
 * HEAD 处反而是<b>后应用（数字大）者先执行</b>：gytrinket 999 → lensouls 1000 → 我们排在它前面。
 * 目标类不在编译依赖里，故用 {@code targets} 字符串 + {@code remap = false}；
 * 配置在 {@code lensouls.compat.mixins.json} 的 <b>client</b> 数组中（该类是客户端专属），
 * {@code required=false} + {@code require = 0}：模组缺席或改版都不影响启动。
 */
@Mixin(targets = "com.gytrinket.gytrinket.client.attack_mode.charged_attack.ChargedAttackInputHandler",
        remap = false)
public class GytrinketCameraChargeMixin {

    /** 左键充能入口（由对方 MinecraftClientMixin 在 startAttack 里调用） */
    @Inject(method = "startCharging", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lensouls$skipChargingWithCamera(CallbackInfo ci) {
        if (lensouls$holdingCamera()) ci.cancel();
    }

    /** 右键充能入口（由对方 MouseHandlerMixin 在任意右键按下时调用） */
    @Inject(method = "startChargingFromRightButton", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void lensouls$skipRightChargingWithCamera(CallbackInfo ci) {
        if (lensouls$holdingCamera()) ci.cancel();
    }

    /** 主手或副手是否拿着相机（含拍立得）。 */
    @Unique
    private static boolean lensouls$holdingCamera() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) return false;
        return com.plumejade.lensouls.ability.handler.CameraInputHandler.isCamera(player.getMainHandItem())
                || com.plumejade.lensouls.ability.handler.CameraInputHandler.isCamera(player.getOffhandItem());
    }
}
