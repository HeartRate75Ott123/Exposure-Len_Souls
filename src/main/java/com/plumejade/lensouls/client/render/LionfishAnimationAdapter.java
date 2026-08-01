package com.plumejade.lensouls.client.render;

import com.github.L_Ender.lionfishapi.server.animation.Animation;
import com.github.L_Ender.lionfishapi.server.animation.IAnimatedEntity;
import com.plumejade.lensouls.entity.BossPhantomEntity;

import java.lang.reflect.Proxy;

/**
 * 将 {@link BossPhantomEntity} 桥接为 LionfishAPI {@link IAnimatedEntity}（动态代理）。
 * <p>
 * BossPhantomEntity 不再实现 IAnimatedEntity（避免无 lionfishapi 时服务端实体注册崩溃），
 * 动画仅在灾变/lionfishapi 存在时使用，此适配器也只会被客户端渲染链（灾变已装时）加载。
 */
public final class LionfishAnimationAdapter {

    private LionfishAnimationAdapter() {}

    public static IAnimatedEntity adapt(BossPhantomEntity entity) {
        return (IAnimatedEntity) Proxy.newProxyInstance(
                IAnimatedEntity.class.getClassLoader(),
                new Class<?>[]{IAnimatedEntity.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getAnimationTick": return entity.getAnimationTick();
                        case "setAnimationTick": entity.setAnimationTick((Integer) args[0]); return null;
                        case "getAnimation": return entity.getAnimation();
                        case "setAnimation": entity.setAnimation(args[0]); return null;
                        case "getAnimations": return new Animation[0];
                        default: return null;
                    }
                });
    }
}
