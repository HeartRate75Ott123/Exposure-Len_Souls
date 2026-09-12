package com.plumejade.lensouls.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 取消 Field Guide 自带的「照片解锁图鉴」重扫描路径。
 * <p>
 * 被取消的 {@code ExposureCompat.unlockContentInFrame} 每张照片都会：
 * <ol>
 *   <li>打 1 条准星射线 + <b>7×7 = 49 条</b>跨 50°、长 <b>256 格</b>的射线；</li>
 *   <li>把射线命中的<b>方块</b>（花草、地面、建筑等环境）也加进解锁集合；</li>
 *   <li>对每个目标做复合条目打分与解锁。</li>
 * </ol>
 * 这既是「拍到花草」的根因，也是严重卡顿的根因。
 * <p>
 * 替代实现：{@code PhotoInjectionHandler} 在能力为 {@code WILD_GLIMPSE}（见微知著）时，
 * 直接把相机帧内的<b>生物</b>交给 {@link com.plumejade.lensouls.integration.FieldGuideBridge}
 * 解锁条目。数据源是本模组已优化的视锥（遇方块即止 + 硬射程上限），零额外射线。
 * <p>
 * <b>解锁不受图鉴任何配置或条目规则约束</b>：既不读 {@code exposureUnlockViaPhotograph}
 * 这类开关（否则等于把「能不能解锁」交给对方配置），也不接受条目自身的触发条件/前置限制——
 * 用能力拍了照，画面内的生物必定解锁。
 * <p>
 * 本 mixin 位于 {@code lensouls.compat.mixins.json}（required=false），
 * 图鉴未安装时不会加载，也不报错。
 */
@Mixin(targets = "com.evandev.fieldguide.compat.exposure.ExposureCompat", remap = false)
public class FieldGuideExposureMixin {

    @Inject(method = "unlockContentInFrame", at = @At("HEAD"), cancellable = true, require = 0)
    private static void lensouls$skipHeavyEnvironmentScan(CallbackInfo ci) {
        // 图鉴的环境重扫描整体停用：环境内容不再被解锁，卡顿根因移除。
        // 生物解锁改由见微知著能力经 FieldGuideBridge 完成。
        ci.cancel();
    }
}
