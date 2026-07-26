package com.plumejade.lensouls.client.phantom;

import com.github.L_Ender.lionfishapi.server.animation.IAnimatedEntity;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 客户端假身驱动器——生一个真 BOSS 实体做粒子/音效引擎，不加入世界不渲染。
 * 通过反射调用其 SwingParticles() 方法，利用原版粒子算法。
 * <p>
 * start() → 每帧 tick() → dispose() 生命周期 60 tick。
 */
public class PhantomEntityDriver {

    // 驱动状态
    @Nullable private Entity dummyEntity;
    private boolean active = false;
    private int animationTick = 0;
    private int lifetimeTicks = 0;
    private int elapsedTicks = 0;

    // 反射缓存
    @Nullable private Method swingParticlesMethod;
    @Nullable private Method shieldSmashMethod;
    @Nullable private Field socketPosArrayField;
    @Nullable private Field prevBladePosField;
    @Nullable private com.github.L_Ender.lionfishapi.server.animation.Animation cachedAnimation;

    // 位置（在外设置，在内同步）
    private Vec3 phantomPos = Vec3.ZERO;
    private float phantomYaw = 0f;
    // 上一帧的剑尖位置（Flameswing 依赖 prevBladePos→socketPosArray[0] 的差值）
    private Vec3 prevBladeWorldPos = Vec3.ZERO;

    // ========== 启动 ==========

    /**
     * 为指定 BOSS 类型启动假身驱动器。
     * 目前仅支持 Ignis。
     */
    public void start(BossPhantomType type, Level level, Vec3 pos, float yaw, int lifetimeTicks) {
        if (active) dispose();

        this.phantomPos = pos;
        this.phantomYaw = yaw;
        this.lifetimeTicks = lifetimeTicks;
        this.elapsedTicks = 0;
        this.animationTick = 0;

        if (type == BossPhantomType.IGNIS) {
            startIgnis(level, pos);
        }
        // 其他 BOSS 推广时添加
    }

    private void startIgnis(Level level, Vec3 pos) {
        try {
            // 1. 反射获取 EntityType<Ignis_Entity>
            Class<?> modEntities = Class.forName("com.github.L_Ender.cataclysm.init.ModEntities");
            java.util.function.Supplier<?> ignisHolder =
                    (java.util.function.Supplier<?>) modEntities.getDeclaredField("IGNIS").get(null);
            Object ignisType = ignisHolder.get();

            // 2. 构造 Ignis_Entity(EntityType, Level)
            Class<?> ignisClass = Class.forName("com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity");
            var ctor = ignisClass.getDeclaredConstructor(
                    net.minecraft.world.entity.EntityType.class, Level.class);
            dummyEntity = (Entity) ctor.newInstance(ignisType, level);

            // 3. 配置：不渲染、无重力
            dummyEntity.setNoGravity(true);
            dummyEntity.setInvisible(true);
            dummyEntity.setPos(pos.x, pos.y, pos.z);

            // 4. 获取 Animation 引用 + 设置
            String animClass = "com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity";
            cachedAnimation = getAnimationField(animClass, "SPIN_ATTACK");
            if (cachedAnimation != null && dummyEntity instanceof IAnimatedEntity animEntity) {
                animEntity.setAnimation(cachedAnimation);
                animEntity.setAnimationTick(0);
            }

            // 5. socketPosArray 初始化（public Vec3[] 字段）
            socketPosArrayField = findField(dummyEntity.getClass(), "socketPosArray");
            if (socketPosArrayField != null) {
                socketPosArrayField.set(dummyEntity, new Vec3[]{pos, pos, pos});
            }

            // 6. prevBladePos 初始化为当前位置稍偏一点（首帧就有差值产生粒子）
            prevBladePosField = ignisClass.getDeclaredField("prevBladePos");
            prevBladePosField.setAccessible(true);
            prevBladePosField.set(dummyEntity, pos.add(0, 2.5, -0.5));
            prevBladeWorldPos = pos.add(0, 2.5, 0); // 首帧剑尖位置

            // 7. 反射获取 SwingParticles() + ShieldSmashparticle() 私有方法
            swingParticlesMethod = ignisClass.getDeclaredMethod("SwingParticles");
            swingParticlesMethod.setAccessible(true);
            shieldSmashMethod = ignisClass.getDeclaredMethod("ShieldSmashparticle", float.class, float.class, float.class);
            shieldSmashMethod.setAccessible(true);

            // 8. 设置动画（通过 IAnimatedEntity 接口）
            if (cachedAnimation != null && dummyEntity instanceof IAnimatedEntity anim) {
                anim.setAnimation(cachedAnimation);
            }

            active = true;

        } catch (Throwable t) {
            LenSouls.LOGGER.error("[假身] Ignis 启动失败", t);
            dispose();
        }
    }

    // ========== 每帧驱动 ==========

    /** 每客户端 tick 调用一次 */
    public void tick() {
        if (!active || dummyEntity == null) return;

        elapsedTicks++;
        animationTick++;

        try {
            // 1. 更新假身位置
            dummyEntity.setPos(phantomPos.x, phantomPos.y, phantomPos.z);

            // 2. socketPosArray 直接用假身位置+头顶偏移
            // Flameswing 靠 prevBladePos→socketPosArray[0] 差值算粒子
            // 两帧间差值越大 → 分段数越多 → 粒子越密
            Vec3 currentTip = phantomPos.add(0, 2.5, 0);
            if (socketPosArrayField != null) {
                socketPosArrayField.set(dummyEntity, new Vec3[]{currentTip});
            }
            if (prevBladePosField != null) {
                prevBladePosField.set(dummyEntity, prevBladeWorldPos);
            }
            // 每帧随机微移产生差值（Flameswing 靠差值决定粒子量）
            prevBladeWorldPos = currentTip.add(
                    (dummyEntity.getRandom().nextDouble() - 0.5) * 0.8,
                    (dummyEntity.getRandom().nextDouble() - 0.5) * 0.3,
                    (dummyEntity.getRandom().nextDouble() - 0.5) * 0.8);

            // 3. 更新 animationTick（IAnimatedEntity）
            if (dummyEntity instanceof IAnimatedEntity animEntity) {
                animEntity.setAnimationTick(animationTick);
            }

            // 4. SPIN_ATTACK: Flameswing 在 tick 10~18（SwingParticles 内判断）
            if (swingParticlesMethod != null && animationTick >= 10 && animationTick < 18) {
                swingParticlesMethod.invoke(dummyEntity);
            }

            // 5. 音效（假身不在世界内，playSound 传不到玩家耳朵，手动播）
            // SPIN_ATTACK: STRONGSWING 在 tick 27
            if (animationTick == 27) {
                try {
                    var swingS = com.github.L_Ender.cataclysm.init.ModSounds.STRONGSWING.get();
                    var player = net.minecraft.client.Minecraft.getInstance().player;
                    if (player != null) {
                        dummyEntity.level().playSeededSound(player, phantomPos.x, phantomPos.y, phantomPos.z,
                            swingS, net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.8f,
                            dummyEntity.getRandom().nextLong());
                    }
                } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            LenSouls.LOGGER.error("[假身] tick 出错", t);
            dispose();
        }

        // 超过生命周期自动丢弃
        if (elapsedTicks >= lifetimeTicks) {
            dispose();
        }
    }

    /** 是否激活 */
    public boolean isActive() { return active; }

    // ========== 清理 ==========

    /** 丢弃假身，释放引用 */
    public void dispose() {
        active = false;
        dummyEntity = null;
        swingParticlesMethod = null;
        shieldSmashMethod = null;
        socketPosArrayField = null;
        prevBladePosField = null;
        animationTick = 0;
        elapsedTicks = 0;
    }

    // ========== 反射辅助 ==========

    @Nullable
    private static Field findField(Class<?> clazz, String name) {
        for (var c = clazz; c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    @Nullable
    private static com.github.L_Ender.lionfishapi.server.animation.Animation getAnimationField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
            return (com.github.L_Ender.lionfishapi.server.animation.Animation) f.get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
