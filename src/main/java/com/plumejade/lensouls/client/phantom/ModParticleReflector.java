package com.plumejade.lensouls.client.phantom;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * 跨模组粒子/音效反射访问器。
 * <p>
 * 通过反射从灾变/传奇怪物模组的注册类中获取粒子类型和音效，
 * 避免编译期硬依赖带来的 ClassNotFoundException。
 * 反射结果缓存，只在第一次访问时反射。
 */
public class ModParticleReflector {

    // ===== Cataclysm 粒子 =====
    @Nullable private static SimpleParticleType cataclysmSoulLava;
    @Nullable private static SimpleParticleType cataclysmSpark;
    @Nullable private static SimpleParticleType cataclysmIgnisExplode;
    @Nullable private static SimpleParticleType cataclysmIgnisAbyssExplode;
    @Nullable private static SimpleParticleType cataclysmIgnisSoulExplode;
    private static boolean cataclysmParticleResolved = false;

    // ===== Cataclysm 音效 =====
    @Nullable private static SoundEvent cataclysmSwordStomp;
    @Nullable private static SoundEvent cataclysmStrongSwing;
    private static boolean cataclysmSoundResolved = false;

    // ===== Legendary Monsters 粒子 =====
    @Nullable private static SimpleParticleType lmBeam;
    @Nullable private static SimpleParticleType lmChorusSmoke;
    @Nullable private static SimpleParticleType lmGhostlySoul;
    @Nullable private static SimpleParticleType lmSoulStrike;
    @Nullable private static SimpleParticleType lmAnnihilationFlameStrike;
    @Nullable private static SimpleParticleType lmBigAnnihilationFlame;
    @Nullable private static SimpleParticleType lmAnnihilationExplosion;
    @Nullable private static SimpleParticleType lmAnnihilationGeyser;
    private static boolean lmParticleResolved = false;

    // =================================================================
    // 反射获取 Cataclysm 粒子
    // =================================================================

    /** 尝试通过反射获取灾变 ModParticle 类中的所有注册粒子 */
    private static void resolveCataclysmParticles() {
        if (cataclysmParticleResolved) return;
        cataclysmParticleResolved = true;
        if (!ModList.get().isLoaded("cataclysm")) return;
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.init.ModParticle");
            cataclysmSoulLava = getParticleField(clazz, "SOUL_LAVA");
            cataclysmSpark = getParticleField(clazz, "SPARK");
            cataclysmIgnisExplode = getParticleField(clazz, "IGNIS_EXPLODE");
            cataclysmIgnisAbyssExplode = getParticleField(clazz, "IGNIS_ABYSS_EXPLODE");
            cataclysmIgnisSoulExplode = getParticleField(clazz, "IGNIS_SOUL_EXPLODE");
        } catch (Throwable t) {
        }
    }

    /** 反射获取灾变音效 */
    private static void resolveCataclysmSounds() {
        if (cataclysmSoundResolved) return;
        cataclysmSoundResolved = true;
        if (!ModList.get().isLoaded("cataclysm")) return;
        try {
            Class<?> clazz = Class.forName("com.github.L_Ender.cataclysm.init.ModSounds");
            cataclysmSwordStomp = getSoundField(clazz, "SWORD_STOMP");
            cataclysmStrongSwing = getSoundField(clazz, "STRONGSWING");
        } catch (Throwable t) {
        }
    }

    /** 反射获取 LM 粒子 */
    private static void resolveLmParticles() {
        if (lmParticleResolved) return;
        lmParticleResolved = true;
        if (!ModList.get().isLoaded("legendary_monsters")) return;
        try {
            Class<?> clazz = Class.forName("net.miauczel.legendary_monsters.Particle.ModParticles");
            lmBeam = getParticleField(clazz, "BEAM");
            lmChorusSmoke = getParticleField(clazz, "CHORUS_SMOKE");
            lmGhostlySoul = getParticleField(clazz, "GHOSTLY_SOUL");
            lmSoulStrike = getParticleField(clazz, "SOULSTRIKE");
            lmAnnihilationFlameStrike = getParticleField(clazz, "ANNIHILATION_FLAME_STRIKE");
            lmBigAnnihilationFlame = getParticleField(clazz, "BIG_ANNIHILATION_FLAME");
            lmAnnihilationExplosion = getParticleField(clazz, "ANNIHILATION_EXPLOSION");
            lmAnnihilationGeyser = getParticleField(clazz, "ANNIHILATION_GEYSER");
        } catch (Throwable t) {
        }
    }

    /** 通用：从注册类中取 Supplier 字段并 .get() */
    @Nullable
    private static SimpleParticleType getParticleField(Class<?> registryClass, String fieldName) {
        try {
            Field f = registryClass.getDeclaredField(fieldName);
            Object supplier = f.get(null); // 静态字段
            if (supplier instanceof java.util.function.Supplier<?> s) {
                Object result = s.get();
                if (result instanceof SimpleParticleType pt) return pt;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 通用：从注册类中取 Holder 字段并 get() 音效 */
    @Nullable
    private static SoundEvent getSoundField(Class<?> registryClass, String fieldName) {
        try {
            Field f = registryClass.getDeclaredField(fieldName);
            Object holder = f.get(null);
            if (holder instanceof net.minecraft.core.Holder<?> h) {
                Object result = h.value();
                if (result instanceof SoundEvent se) return se;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // =================================================================
    // 公共访问方法
    // =================================================================

    @Nullable
    public static SimpleParticleType getSoulLava() {
        resolveCataclysmParticles();
        return cataclysmSoulLava;
    }

    @Nullable
    public static SimpleParticleType getSpark() {
        resolveCataclysmParticles();
        return cataclysmSpark;
    }

    @Nullable
    public static SimpleParticleType getIgnisExplode() {
        resolveCataclysmParticles();
        return cataclysmIgnisExplode;
    }

    @Nullable
    public static SimpleParticleType getIgnisAbyssExplode() {
        resolveCataclysmParticles();
        return cataclysmIgnisAbyssExplode;
    }

    @Nullable
    public static SimpleParticleType getIgnisSoulExplode() {
        resolveCataclysmParticles();
        return cataclysmIgnisSoulExplode;
    }

    @Nullable
    public static SoundEvent getSwordStomp() {
        resolveCataclysmSounds();
        return cataclysmSwordStomp;
    }

    @Nullable
    public static SoundEvent getStrongSwing() {
        resolveCataclysmSounds();
        return cataclysmStrongSwing;
    }

    @Nullable
    public static SimpleParticleType getBeam() {
        resolveLmParticles();
        return lmBeam;
    }

    @Nullable
    public static SimpleParticleType getChorusSmoke() {
        resolveLmParticles();
        return lmChorusSmoke;
    }

    @Nullable
    public static SimpleParticleType getGhostlySoul() {
        resolveLmParticles();
        return lmGhostlySoul;
    }

    @Nullable
    public static SimpleParticleType getSoulStrike() {
        resolveLmParticles();
        return lmSoulStrike;
    }

    @Nullable
    public static SimpleParticleType getAnnihilationFlameStrike() {
        resolveLmParticles();
        return lmAnnihilationFlameStrike;
    }

    @Nullable
    public static SimpleParticleType getBigAnnihilationFlame() {
        resolveLmParticles();
        return lmBigAnnihilationFlame;
    }

    @Nullable
    public static SimpleParticleType getAnnihilationExplosion() {
        resolveLmParticles();
        return lmAnnihilationExplosion;
    }

    @Nullable
    public static SimpleParticleType getAnnihilationGeyser() {
        resolveLmParticles();
        return lmAnnihilationGeyser;
    }
}
