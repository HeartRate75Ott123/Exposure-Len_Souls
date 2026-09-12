package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.ability.handler.PhotoInjectionHandler;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
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
 * 出片 → PhotoInjector.inject(photo, frame, player, applyDisplayName)
 *      → 读取缓存 + AbilityBehavior 决定是否成片、写哪些数据
 * </pre>
 */
public final class PhotoInjector {

    private PhotoInjector() {
    }

    /**
     * 把拍照时缓存的能力数据注入到出片得到的照片上。
     *
     * @param photo            出片的照片（可能为空栈）
     * @param frame            该照片对应的帧
     * @param player           拍摄者（暗房路径由 {@code frame.photographer()} 反查；可为 null）
     * @param applyDisplayName 是否把显示名改写为「{@code 能力名}照片」（拍立得会，暗房保留原名）
     */
    public static void inject(ItemStack photo, Frame frame, ServerPlayer player, boolean applyDisplayName) {
        if (frame == null) return;
        String exposureId = frame.identifier() != null ? frame.identifier().toString() : null;
        if (exposureId == null || exposureId.isEmpty()) return;

        // 先消费能力缓存：无论照片最终成不成立，这一帧的能力都不能留给下一次出片
        AbilityType ability = PhotoInjectionHandler.pollAbility(exposureId);

        if (photo == null || photo.isEmpty()) return;

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
            return;
        }
        if (injected) return;

        boolean hasEntities = frame.entitiesInFrame() != null && !frame.entitiesInFrame().isEmpty();
        boolean doInject = true;

        // 能力窃取：抓到帧内目标 → 照片带上被窃取实体；无实体/无注册效果 → 退化为普通照片
        if (ability == AbilityType.ABILITY_STEAL) {
            String entityId = PhotoInjectionHandler.pollStolenEntity(exposureId);
            if (entityId == null || entityId.isEmpty() || !PhotographEffectRegistry.hasEffect(entityId)) {
                doInject = false;
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
