package com.plumejade.lensouls.integration.jade;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade/WTHIT 集成插件。
 * <p>
 * 为实体叠加层添加镜魂元素弱点信息。
 * 通过 {@link EntityWeaknessComponentProvider} 从数据包读取弱点配置，
 * 在 Jade 面板追加绿色文字（如 "弱火"、"弱水"）。
 */
@WailaPlugin(LenSouls.MODID)
public class LensoulsJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(
                EntityWeaknessComponentProvider.INSTANCE, LivingEntity.class);
    }
}
