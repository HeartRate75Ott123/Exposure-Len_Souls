package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/**
 * 拍照削韧处理器。
 * <p>
 * 通过反射监听 Exposure 的 {@code FrameAddedEvent}（每张照片成功拍摄后触发），
 * 检测画面中的 BOSS 实体并削韧 1 点。
 * <p>
 * 任何相机拍照均可触发削韧，无需附魔或能力（此为模组核心机制）。
 * 弱点透镜能力仅控制拍照后是否将照片标记为可装入剑槽增伤。
 * <p>
 * Stage 3 VITAL_STRIKE 会走独立代码路径（不消耗相纸），直接调用
 * {@link BossToughnessManager#hit(LivingEntity)}，与此监听无冲突。
 */
public class ToughnessPhotoHandler {

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        try {
            Class<?> eventClass = Class.forName("io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent");
            Method getEntitiesInFrame = eventClass.getMethod("getEntitiesInFrame");

            Method addListener = NeoForge.EVENT_BUS.getClass()
                    .getMethod("addListener", EventPriority.class, boolean.class, Class.class, Consumer.class);

            addListener.invoke(NeoForge.EVENT_BUS, EventPriority.NORMAL, false, eventClass,
                    (Consumer<Object>) event -> {
                        try {
                            @SuppressWarnings("unchecked")
                            List<LivingEntity> entities = (List<LivingEntity>) getEntitiesInFrame.invoke(event);
                            if (entities == null || entities.isEmpty()) return;

                            BossToughnessManager manager = BossToughnessManager.getInstance();
                            for (LivingEntity entity : entities) {
                                if (!manager.has(entity)) continue;
                                manager.hit(entity);
                                break;
                            }
                        } catch (Exception e) {
                            LenSouls.LOGGER.error("[ToughnessPhoto] 处理拍照削韧失败", e);
                        }
                    });

        } catch (Exception e) {
            LenSouls.LOGGER.error("[ToughnessPhoto] 注册拍照削韧监听失败（Exposure 未加载？）", e);
        }
    }
}
