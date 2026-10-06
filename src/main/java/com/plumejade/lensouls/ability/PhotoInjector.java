package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.util.PhotoLog;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Map;

/**
 * 出片注入的唯一实现——拍立得与暗房共用。
 * <p>
 * 原先 {@code PolaroidPrintMixin} 与 {@code LightroomInjectMixin} 各维护一份近乎逐行相同的
 * 注入逻辑（元素活性组件、能力窃取标签、弱点透镜空帧降级、能力专属数据、照片改名），
 * 加能力必须两处同改。现两条路径只保留各自的 mixin 注入点，出片逻辑全部委托到这里。
 * <p>
 * 数据流：
 * <pre>
 * FrameAddedEvent → PhotoInjectionHandler 按 exposureId 缓存能力/元素/窃取目标
 * 出片 → PhotoInjector.inject(photo, frame, player, applyDisplayName, path)
 *      → 读取缓存 + AbilityBehavior 决定是否成片、写哪些数据
 * </pre>
 * <p>
 * 失败日志一律走 {@link PhotoLog}（INFO + 按原因限流），见 {@link #inject} 的说明。
 */
public final class PhotoInjector {

    private PhotoInjector() {
    }

    /**
     * 把拍照时缓存的能力数据注入到出片得到的照片上。
     *
     * <p><b>失败日志一律走 {@link PhotoLog}：INFO 可见 + 按原因限流</b>（同一原因 60 秒一条、全场 40 条上限）。
     * 出片是高频动作，这些分支又挂在每次拍照上（没选能力 / 拍到牛 / 空帧都是正常玩法），
     * 所以既不能藏进 debug（FML 默认 debug 只进 {@code logs/debug.log}，排障时没人看），
     * 也不能无脑 INFO（会刷屏）。玩家报「拍不出能力照片」时，看 latest.log 就能判断是<b>哪一端断的</b>：
     * <ul>
     *   <li>「没有能力缓存」⇒ 拍照那一刻 {@code PhotoInjectionHandler} 就没缓存成功
     *       （未选中能力 / 手持相机没摄魂术 / 拍完立刻切手），或缓存已被同帧的另一次打印消费；</li>
     *   <li>「主体没有注册效果条目」⇒ 拍到的生物本来就没有能力照片效果（属设计，不是 bug）；</li>
     *   <li>「没有捕获到照片栈」⇒ 那边的 mixin 注入点没命中（Exposure 版本变了）。</li>
     * </ul>
     *
     * @param photo            出片的照片（可能为空栈）
     * @param frame            该照片对应的帧
     * @param player           拍摄者（暗房路径由 {@code frame.photographer()} 反查；可为 null）
     * @param applyDisplayName 是否把显示名改写为「{@code 能力名}照片」（拍立得会，暗房保留原名）
     * @param path             出片路径标签，仅用于日志区分（{@code polaroid} / {@code lightroom}）
     */
    public static void inject(ItemStack photo, Frame frame, ServerPlayer player, boolean applyDisplayName,
                              String path) {
        if (frame == null) {
            PhotoLog.info("frame-null", () -> "出片未注入（" + path + "）：frame 为空");
            return;
        }
        String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
        if (exposureId == null || exposureId.isEmpty()) {
            PhotoLog.info("no-exposure-id", () -> "出片未注入（" + path + "）：帧没有 exposureId");
            return;
        }

        // 先消费能力缓存：无论照片最终成不成立，这一帧的能力都不能留给下一次出片
        AbilityType ability = PhotoInjectionHandler.pollAbility(exposureId);

        if (photo == null || photo.isEmpty()) {
            PhotoLog.info("no-photo-stack", () -> "出片未注入（" + path + "）：没有捕获到照片栈——"
                    + "对应 mixin 的注入点没命中？（exposureId=" + exposureId + "）");
            return;
        }

        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean injected = tag.getBoolean("lensouls:injected");

        // 通用元素活性组件：普通拍照/能力拍照都注入（组件驱动抑制判定，与 attacker_element 数据对齐）
        Map<ElementDamage, Integer> levels = PhotoInjectionHandler.pollElementLevels(exposureId);
        String elemEntity = PhotoInjectionHandler.pollElementEntity(exposureId);
        if (!injected && levels != null && elemEntity != null && !levels.isEmpty()) {
            CompoundTag el = new CompoundTag();
            for (var en : levels.entrySet()) el.putInt(en.getKey().getSerializedName(), en.getValue());
            tag.put("lensouls:element_levels", el);
            tag.putString("lensouls:element_entity", elemEntity);
            tag.putBoolean("lensouls:photograph_curio", true);
        }

        if (ability == null) {
            // 普通照片：仅补元素组件，不标 injected
            if (tag.contains("lensouls:element_levels")) {
                photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            PhotoLog.info("no-ability-cache", () -> "出片未注入（" + path + "）：没有能力缓存，按普通照片出片"
                    + "（exposureId=" + exposureId + "）。常见原因：拍照时未选中能力 / 手持相机没有摄魂术"
                    + "（或拍完立刻切了手）/ 缓存已被同帧的另一次打印消费");
            return;
        }
        if (injected) {
            PhotoLog.info("already-injected", () -> "出片未重复注入（" + path + "）：这张照片已带 lensouls:injected"
                    + "（exposureId=" + exposureId + "）");
            return;
        }

        boolean hasEntities = frame.entitiesInFrame() != null && !frame.entitiesInFrame().isEmpty();
        boolean doInject = true;

        // 能力窃取：抓到帧内目标 → 照片带上被窃取实体；无实体/无注册效果 → 退化为普通照片
        if (ability == AbilityType.ABILITY_STEAL) {
            String entityId = PhotoInjectionHandler.pollStolenEntity(exposureId);
            if (entityId == null || entityId.isEmpty()) {
                doInject = false;
                PhotoLog.info("steal-no-target", () -> "能力窃取未成片（" + path + "）：拍照时帧内没有可窃取的非玩家实体"
                        + "（exposureId=" + exposureId + "）");
            } else if (!PhotographEffectRegistry.hasEffect(entityId)) {
                doInject = false;
                PhotoLog.info("steal-no-effect", () -> "能力窃取未成片（" + path + "）：主体 " + entityId
                        + " 没有注册的照片效果条目——拍到牛羊/村民这类本来就是普通照片，"
                        + "属设计（exposureId=" + exposureId + "）");
            } else {
                tag.putBoolean("lensouls:ability_steal", true);
                tag.putString("lensouls:stolen_entity", entityId);
                tag.putBoolean("lensouls:photograph_curio", true);
                Boolean isBoss = PhotoInjectionHandler.pollBoss(exposureId);
                if (isBoss != null && isBoss) tag.putBoolean("lensouls:is_boss", true);
            }
        }

        // 空帧降级（弱点透镜等要求帧内有生物的能力）
        if (AbilityBehavior.requiresEntitiesInFrame(ability) && !hasEntities) {
            doInject = false;
            PhotoLog.info("empty-frame", () -> "未成片（" + path + "）：能力 " + ability.getId()
                    + " 要求帧内有生物，但这一帧是空的（exposureId=" + exposureId + "）");
        }

        if (doInject) {
            tag.putBoolean("lensouls:injected", true);
            tag.putString("lensouls:ability_type", ability.getId());

            // 能力专属数据（空间扭曲球心 / 回溯快照 / 后续新能力）
            AbilityBehavior.writePhotoData(ability, frame, player, tag);

            if (applyDisplayName) {
                MutableComponent name = Component.literal("")
                        .append(Component.translatable(ability.getNameKey()))
                        .append(Component.literal("照片"))
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(s -> s.withItalic(false).withBold(false));
                photo.set(DataComponents.CUSTOM_NAME, name);
            }
        }

        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
