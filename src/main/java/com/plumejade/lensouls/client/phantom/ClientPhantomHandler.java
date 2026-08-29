package com.plumejade.lensouls.client.phantom;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.client.PhantomMemoryLeakDiagnostic;
import com.plumejade.lensouls.client.screen.ScreenShakeHandler;
import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * 客户端幻灵技能特效——所有粒子以虚影固定坐标为原点。
 * 跨模组引用：直接引用 + ModList.isLoaded() 守卫 + catch(Throwable) 兜底。
 */
public class ClientPhantomHandler {

    private static final ClientPhantomHandler INSTANCE = new ClientPhantomHandler();

    /** 幻灵实体 ID 集合（用于 mixin 渲染检测，替代不可靠的 persistentData） */
    private static final java.util.Set<Integer> PHANTOM_ENTITY_IDS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private boolean phantomActive = false;
    private Vec3 phantomPos = Vec3.ZERO;
    private float phantomYaw = 0f;

    /** 假身驱动器——驱动原版实体产粒子 */
    private final PhantomEntityDriver driver = new PhantomEntityDriver();

    public static ClientPhantomHandler getInstance() { return INSTANCE; }

    /** Mixin 检测用：指定实体 ID 是否为活跃幻灵 */
    public static boolean isPhantomEntity(int entityId) { return PHANTOM_ENTITY_IDS.contains(entityId); }

    /**
     * Mixin 检测用（实体版）：id 集合 或 实体自身 persistentData 标记。
     * 虚灵实体入世界前服务端已打上 lensouls:phantom 标记并随生成数据同步客户端，
     * 该标记持续整个实体生命周期——即便 stop 包整表清空了 id 集合，存活虚灵仍保持半透明。
     */
    public static boolean isPhantomEntity(Entity entity) {
        if (entity == null) return false;
        return PHANTOM_ENTITY_IDS.contains(entity.getId())
                || entity.getPersistentData().getBoolean("lensouls:phantom");
    }

    /** 注册幻灵（由 LenSoulsClient 的 packet handler 调用） */
    public static void addPhantomEntity(int entityId) { PHANTOM_ENTITY_IDS.add(entityId); }

    /** 注销幻灵 */
    public static void removePhantomEntity(int entityId) { PHANTOM_ENTITY_IDS.remove(entityId); }

    public void startPhantom(java.util.UUID playerId, BossPhantomType type, int lifetimeTicks,
                             double px, double py, double pz, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.getUUID().equals(playerId)) return;
        this.phantomActive = true;
        this.phantomPos = new Vec3(px, py, pz);
        this.phantomYaw = yaw;
        PhantomMemoryLeakDiagnostic.onPhantomStart(type);

        // 启动假身驱动器（有灾变模组时才生效）
        if (ModList.get().isLoaded("cataclysm") || ModList.get().isLoaded("legendary_monsters")) {
            driver.start(type, mc.level, phantomPos, yaw, lifetimeTicks);
        }
    }

    public void playSkill(BossPhantomType type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !phantomActive) return;
        Level level = mc.level;
        if (level == null) return;

        Vec3 pos = phantomPos;
        boolean c = ModList.get().isLoaded("cataclysm");
        boolean lm = ModList.get().isLoaded("legendary_monsters");

        switch (type) {
            case IGNIS -> playIgnisExecute(level, pos, c);
            case CLOUD_GOLEM -> playCloudGolemExecute(level, pos, lm);
            case POSSESSED_PALADIN -> playPaladinExecute(level, pos, lm);
            case OBLITERATOR -> playObliteratorExecute(level, pos, lm);
            case ENDER_GUARDIAN -> playEnderGuardianExecute(level, pos, c);
            case NETHERITE_MONSTROSITY -> playMonstrosityExecute(level, pos, c);
        }
    }

    public void playPhase(BossPhantomType type, int phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !phantomActive) return;
        Level level = mc.level;
        if (level == null) return;

        Vec3 pos = phantomPos;
        boolean c = ModList.get().isLoaded("cataclysm");
        boolean lm = ModList.get().isLoaded("legendary_monsters");

        if (phase == 0) {
            switch (type) {
                case IGNIS -> playIgnisCharge(level, pos, c);
                case CLOUD_GOLEM -> playCloudGolemCharge(level, pos, lm);
                case POSSESSED_PALADIN -> playPaladinCharge(level, pos, lm);
                case OBLITERATOR -> playObliteratorCharge(level, pos, lm);
                case ENDER_GUARDIAN -> playEnderGuardianCharge(level, pos, c);
                case NETHERITE_MONSTROSITY -> playMonstrosityCharge(level, pos, c);
            }
        } else if (phase == 2) {
            switch (type) {
                case IGNIS -> playIgnisDecay(level, pos, c);
                case CLOUD_GOLEM -> playCloudGolemDecay(level, pos, lm);
                case POSSESSED_PALADIN -> playPaladinDecay(level, pos, lm);
                case OBLITERATOR -> playObliteratorDecay(level, pos, lm);
                case ENDER_GUARDIAN -> playEnderGuardianDecay(level, pos, c);
                case NETHERITE_MONSTROSITY -> playMonstrosityDecay(level, pos, c);
            }
        }
    }

    public void stopPhantom() {
        this.phantomActive = false;
        PHANTOM_ENTITY_IDS.clear();
        driver.dispose();
        PhantomMemoryLeakDiagnostic.onPhantomStop();
    }

    public void reset() {
        this.phantomActive = false;
        PHANTOM_ENTITY_IDS.clear();
        driver.dispose();
        PhantomMemoryLeakDiagnostic.onPhantomStop();
    }

    /** 每客户端 tick 驱动假身 */
    public void tickDriver() {
        if (!phantomActive) return;
        driver.tick();
    }

    /**
     * 由 LenSoulsClient 注册到 NeoForge.EVENT_BUS。
     * PlayerTickEvent.Post 每客户端 tick 触发一次。
     */
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide) {
            INSTANCE.tickDriver();
            PhantomMemoryLeakDiagnostic.clientTick();
        }
    }

    /**
     * 客户端断线时清理幻灵状态（重置幻灵标志和实体 ID 集合）。
     * 由 LenSoulsClient 注册到 NeoForge.EVENT_BUS。
     */
    public static void onClientLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level().isClientSide) {
            INSTANCE.reset();
        }
    }

    // =================================================================
    // Ignis —— 参考灾变原版 Ignis_Entity 攻击模式：
    //   ShieldSmashparticle → Ring 冲击波环 + Block 碎块
    //   Flameswing          → 剑弧轨迹插值火焰粒子
    //   Sphereparticle      → SoulLava 球壳
    // =================================================================

    private void playIgnisCharge(Level level, Vec3 pos, boolean c) {
        double g = pos.y - 1.5;
        for (int i = 0; i < 12; i++) {
            float a = level.random.nextFloat() * (float)(2 * Math.PI);
            float d = 0.5f + level.random.nextFloat() * 3f;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + Math.cos(a)*d, g+0.1, pos.z+Math.sin(a)*d,
                    Math.cos(a)*0.05, 0.02, Math.sin(a)*0.05);
        }
        for (int i = 0; i < 5; i++) {
            double dx = (level.random.nextDouble()-0.5)*3, dz = (level.random.nextDouble()-0.5)*3;
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    pos.x+dx, g+level.random.nextDouble()*1.5, pos.z+dz, dx*0.03, 0.08, dz*0.03);
        }
    }

    private void playIgnisDecay(Level level, Vec3 pos, boolean c) {
        double g = pos.y - 1.5;
        for (int i = 0; i < 8; i++) {
            double dx = (level.random.nextDouble()-0.5)*3, dz = (level.random.nextDouble()-0.5)*3;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x+dx, g+0.05, pos.z+dz, dx*0.01, 0.01, dz*0.01);
        }
        for (int i = 0; i < 6; i++) {
            double dx = (level.random.nextDouble()-0.5)*4, dz = (level.random.nextDouble()-0.5)*4;
            level.addParticle(ParticleTypes.FLAME, pos.x+dx, g+level.random.nextDouble()*0.5, pos.z+dz, dx*0.05, 0.02, dz*0.05);
        }
    }

    /** 爆发：禁用——由假身驱动器的 SwingParticles() → Flameswing() 原生替代 */
    private void playIgnisExecute(Level level, Vec3 pos, boolean c) {
        // 假身驱动器已在 ClientPhantomHandler.startPhantom() 中启动
        // 其 driver.tick() 中反射调 Ignis_Entity.SwingParticles()
        // 内部判断当前动画+帧→调 Flameswing() 出原版粒子
    }

    // =================================================================
    // 云筑魔像 (TODO 推广阶段)
    // =================================================================

    private void playCloudGolemCharge(Level level, Vec3 pos, boolean lm) {}
    private void playCloudGolemDecay(Level level, Vec3 pos, boolean lm) {}

    private void playCloudGolemExecute(Level level, Vec3 pos, boolean lm) {
        float yawRad = phantomYaw * Mth.DEG_TO_RAD;
        if (lm) try {
            var beam = net.miauczel.legendary_monsters.Particle.ModParticles.BEAM.get();
            var chorus = net.miauczel.legendary_monsters.Particle.ModParticles.CHORUS_SMOKE.get();
            for (int i = 0; i < 80; i++) {
                float a = yawRad + (level.random.nextFloat()-0.5f)*0.15f;
                float dist = 1f+level.random.nextFloat()*18f, yOff = (level.random.nextFloat()-0.5f)*1.5f;
                level.addParticle(beam, pos.x+Math.sin(a)*dist, pos.y+0.8+yOff, pos.z+Math.cos(a)*dist, Math.sin(a)*0.1, yOff*0.02, Math.cos(a)*0.1);
            }
            for (int i = 0; i < 20; i++) {
                float a = yawRad+(level.random.nextFloat()-0.5f)*0.3f, dist = 1f+level.random.nextFloat()*4f;
                level.addParticle(chorus, pos.x+Math.sin(a)*dist, pos.y+0.5+level.random.nextDouble()*1.5, pos.z+Math.cos(a)*dist, 0, 0.15+level.random.nextDouble()*0.2, 0);
            }
        } catch (Throwable ignored) {}
        for (int r = 0; r < 7; r++) {
            float radius = 2f+r*2.5f;
            for (int i = 0; i < 10+r*4; i++) {
                float a = 2f*(float)Math.PI*i/(10+r*4);
                level.addParticle(ParticleTypes.WAX_OFF, pos.x+Math.cos(a)*radius, pos.y+0.2+r*0.25, pos.z+Math.sin(a)*radius, Math.cos(a)*0.3, 0.05, Math.sin(a)*0.3);
                level.addParticle(ParticleTypes.CLOUD, pos.x+Math.cos(a)*radius, pos.y+0.5+r*0.2, pos.z+Math.sin(a)*radius, 0, 0.05, 0);
            }
        }
        var p = Minecraft.getInstance().player;
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 0.5f, level.random.nextLong());
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0f, 0.8f, level.random.nextLong());
    }

    // =================================================================
    // 圣骑 (TODO 推广阶段)
    // =================================================================

    private void playPaladinCharge(Level level, Vec3 pos, boolean lm) {}
    private void playPaladinDecay(Level level, Vec3 pos, boolean lm) {}

    private void playPaladinExecute(Level level, Vec3 pos, boolean lm) {
        if (lm) try {
            var ghostly = net.miauczel.legendary_monsters.Particle.ModParticles.GHOSTLY_SOUL.get();
            var strike = net.miauczel.legendary_monsters.Particle.ModParticles.SOULSTRIKE.get();
            for (int i = 0; i < 25; i++) {
                double dx = (level.random.nextDouble()-0.5)*5, dz = (level.random.nextDouble()-0.5)*5;
                level.addParticle(ghostly, pos.x+dx, pos.y+level.random.nextDouble()*1.5, pos.z+dz, dx*0.1, 0.05, dz*0.1);
            }
            for (int i = 0; i < 8; i++) {
                float a = level.random.nextFloat()*(float)(2*Math.PI), r = 2f+level.random.nextFloat()*2f;
                level.addParticle(strike, pos.x+Math.cos(a)*r, pos.y+0.3, pos.z+Math.sin(a)*r, Math.cos(a)*0.3, 0.05, Math.sin(a)*0.3);
            }
            for (int i = 0; i < 20; i++) {
                float a = 2f*(float)Math.PI*i/20;
                level.addParticle(
                    new net.miauczel.legendary_monsters.Particle.custom.SoulSigil.RingData(a, 0f, 20, 0.2f, 0.8f, 0.9f, 0.8f, 2f, true,
                        net.miauczel.legendary_monsters.Particle.custom.SoulSigil.EnumRingBehavior.GROW),
                    pos.x+Math.cos(a)*2.5f, pos.y+0.1f, pos.z+Math.sin(a)*2.5f, 0, 0, 0);
            }
        } catch (Throwable ignored) {}
        for (int i = 0; i < 80; i++) {
            float a = level.random.nextFloat()*(float)(2*Math.PI), dist = 1.5f+level.random.nextFloat()*8.5f;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.x+Math.cos(a)*dist, pos.y+0.5+level.random.nextDouble()*0.8, pos.z+Math.sin(a)*dist, Math.cos(a)*0.2, 0.05, Math.sin(a)*0.2);
            level.addParticle(ParticleTypes.SOUL, pos.x+Math.cos(a)*dist*0.5, pos.y+0.3+level.random.nextDouble()*0.3, pos.z+Math.sin(a)*dist*0.5, Math.cos(a)*0.1, 0.02, Math.sin(a)*0.1);
        }
        var p = Minecraft.getInstance().player;
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.4f, level.random.nextLong());
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.5f, 0.8f, level.random.nextLong());
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0f, 0.6f, level.random.nextLong());
    }

    // =================================================================
    // 湮灭 (TODO 推广阶段)
    // =================================================================

    private void playObliteratorCharge(Level level, Vec3 pos, boolean lm) {}
    private void playObliteratorDecay(Level level, Vec3 pos, boolean lm) {}

    private void playObliteratorExecute(Level level, Vec3 pos, boolean lm) {
        if (lm) try {
            var flame = net.miauczel.legendary_monsters.Particle.ModParticles.ANNIHILATION_FLAME_STRIKE.get();
            var big = net.miauczel.legendary_monsters.Particle.ModParticles.BIG_ANNIHILATION_FLAME.get();
            var beam = net.miauczel.legendary_monsters.Particle.ModParticles.BEAM.get();
            var exp = net.miauczel.legendary_monsters.Particle.ModParticles.ANNIHILATION_EXPLOSION.get();
            var geyser = net.miauczel.legendary_monsters.Particle.ModParticles.ANNIHILATION_GEYSER.get();
            for (int i = 0; i < 40; i++) {
                double dx = (level.random.nextDouble()-0.5)*6, dz = (level.random.nextDouble()-0.5)*6;
                level.addParticle(flame, pos.x+dx, pos.y+level.random.nextDouble()*2.5, pos.z+dz, dx*0.1, 0.25, dz*0.1);
            }
            level.addParticle(big, pos.x, pos.y+0.5, pos.z, 0, 0.3, 0);
            level.addParticle(exp, pos.x, pos.y+1, pos.z, 0, 0, 0);
            for (int i = 0; i < 15; i++) {
                double dx = (level.random.nextDouble()-0.5)*3, dz = (level.random.nextDouble()-0.5)*3;
                level.addParticle(geyser, pos.x+dx, pos.y, pos.z+dz, dx*0.05, 0.6+level.random.nextDouble(), dz*0.05);
            }
            for (int i = 0; i < 12; i++) {
                float a = level.random.nextFloat()*(float)(2*Math.PI), r = 2f+level.random.nextFloat()*2f;
                level.addParticle(
                    new net.miauczel.legendary_monsters.Particle.custom.AnnihilationSweepParticle.SweepData(2.5f, a, 0.2f),
                    pos.x+Math.cos(a)*r, pos.y+level.random.nextDouble(), pos.z+Math.sin(a)*r, 0, 0, 0);
            }
            for (int i = 0; i < 40; i++) {
                float a = level.random.nextFloat()*(float)(2*Math.PI), r = 1.5f+level.random.nextFloat()*3f;
                level.addParticle(beam, pos.x+Math.cos(a)*r, pos.y+level.random.nextDouble()*1.5, pos.z+Math.sin(a)*r, Math.cos(a)*0.6, 0.15, Math.sin(a)*0.6);
            }
        } catch (Throwable ignored) {}
        for (int b = 0; b < 16; b++) {
            float theta = (float)(2*Math.PI*b/16), phi = (float)((level.random.nextDouble()-0.5)*Math.PI*0.6);
            double vx = Math.sin(theta)*Math.cos(phi), vy = Math.sin(phi), vz = Math.cos(theta)*Math.cos(phi);
            for (int s = 0; s < 10; s++) {
                double t = 1+s*0.6;
                level.addParticle(ParticleTypes.END_ROD, pos.x+vx*t, pos.y+vy*t, pos.z+vz*t, vx*0.6, vy*0.6+0.1, vz*0.6);
            }
        }
        if (lm) try {
            var p2 = Minecraft.getInstance().player;
            var cS = net.miauczel.legendary_monsters.sound.ModSounds.ANNIHILATION_LASER_CHARGE.get();
            var sS = net.miauczel.legendary_monsters.sound.ModSounds.ANNIHILATION_LASER_SINGLE_SHOOT.get();
            level.playSeededSound(p2, pos.x, pos.y, pos.z, cS, SoundSource.PLAYERS, 2f, 1f, level.random.nextLong());
            level.playSeededSound(p2, pos.x, pos.y, pos.z, sS, SoundSource.PLAYERS, 3f, 0.8f, level.random.nextLong());
        } catch (Throwable e) {
            var p2 = Minecraft.getInstance().player;
            level.playSeededSound(p2, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.5f, 0.5f, level.random.nextLong());
            level.playSeededSound(p2, pos.x, pos.y, pos.z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 2f, 1.5f, level.random.nextLong());
            level.playSeededSound(p2, pos.x, pos.y, pos.z, SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, 1f, 0.8f, level.random.nextLong());
        }
    }

    // =================================================================
    // 末影守卫 (TODO 推广阶段)
    // =================================================================

    private void playEnderGuardianCharge(Level level, Vec3 pos, boolean c) {}
    private void playEnderGuardianDecay(Level level, Vec3 pos, boolean c) {}

    private void playEnderGuardianExecute(Level level, Vec3 pos, boolean c) {
        double gy = pos.y - 1.3;
        spawnAttackParticlesGround(level, new Vec3(pos.x, gy, pos.z));
        if (c) try {
            var spark = com.github.L_Ender.cataclysm.init.ModParticle.SPARK.get();
            level.addParticle(
                new com.github.L_Ender.cataclysm.client.particle.Options.RingParticleOptions(0f, (float)Math.PI/2f, 30, 220, 200, 255, 1f, 20f, false, 0),
                pos.x, gy+0.1, pos.z, 0, 0, 0);
            for (int i = 0; i < 40; i++) {
                float a = level.random.nextFloat()*(float)(2*Math.PI), r = 1f+level.random.nextFloat()*4f;
                level.addParticle(spark, pos.x+Math.cos(a)*r, gy+0.1, pos.z+Math.sin(a)*r, Math.cos(a)*0.15, 0.05, Math.sin(a)*0.15);
            }
        } catch (Throwable ignored) {}
        for (int ring = 0; ring < 4; ring++) {
            float radius = 1.5f+ring*1.5f;
            for (int i = 0; i < 30; i++) {
                float a = 2f*(float)Math.PI*i/30;
                level.addParticle(ParticleTypes.PORTAL, pos.x+Math.cos(a)*radius, gy+0.05, pos.z+Math.sin(a)*radius, 0, 0, 0);
            }
        }
        for (int i = 0; i < 40; i++) {
            float a = level.random.nextFloat()*(float)(2*Math.PI), r = 2f+level.random.nextFloat()*4f;
            level.addParticle(ParticleTypes.PORTAL, pos.x+Math.cos(a)*r, gy+0.05+level.random.nextDouble()*0.1, pos.z+Math.sin(a)*r, -Math.cos(a)*0.15, 0, -Math.sin(a)*0.15);
        }
        for (int i = 0; i < 30; i++) {
            float a = 2f*(float)Math.PI*i/30;
            level.addParticle(ParticleTypes.END_ROD, pos.x+Math.cos(a)*4.5f, gy+0.1, pos.z+Math.sin(a)*4.5f, Math.cos(a)*0.3, 0.1, Math.sin(a)*0.3);
        }
        var p = Minecraft.getInstance().player;
        if (c) try {
            level.playSeededSound(p, pos.x, pos.y, pos.z, com.github.L_Ender.cataclysm.init.ModSounds.STRONGSWING.get(), SoundSource.PLAYERS, 1.5f, 0.8f, level.random.nextLong());
        } catch (Throwable ignored) {
            level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 1.0f, 0.7f, level.random.nextLong());
            level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.5f, 1.3f, level.random.nextLong());
            level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.8f, level.random.nextLong());
        }
    }

    // =================================================================
    // 下界合金巨兽 (TODO 推广阶段)
    // =================================================================

    private void playMonstrosityCharge(Level level, Vec3 pos, boolean c) {}
    private void playMonstrosityDecay(Level level, Vec3 pos, boolean c) {}

    private void playMonstrosityExecute(Level level, Vec3 pos, boolean c) {
        if (c) try {
            var spark = com.github.L_Ender.cataclysm.init.ModParticle.SPARK.get();
            var lava = com.github.L_Ender.cataclysm.init.ModParticle.SOUL_LAVA.get();
            for (int i = 0; i < 25; i++) {
                double dx = (level.random.nextDouble()-0.5)*6, dz = (level.random.nextDouble()-0.5)*6;
                level.addParticle(spark, pos.x+dx, pos.y+level.random.nextDouble()*0.5, pos.z+dz, dx*0.08, 0.5+level.random.nextDouble(), dz*0.08);
            }
            for (int i = 0; i < 15; i++) {
                double dx = (level.random.nextDouble()-0.5)*4, dz = (level.random.nextDouble()-0.5)*4;
                level.addParticle(
                    new com.github.L_Ender.cataclysm.client.particle.Options.CustomPoofParticleOptions(180, 160, 140, 0.3f),
                    pos.x+dx, pos.y+level.random.nextDouble()*0.3, pos.z+dz, dx*0.1, 0.3, dz*0.1);
            }
            for (int h = 0; h < 14; h++) {
                level.addParticle(lava, pos.x, pos.y+0.5+h*0.5, pos.z, 0, 0.3+h*0.05, 0);
            }
        } catch (Throwable ignored) {}
        for (int h = 0; h < 14; h++) {
            double height = 0.5+h*0.5;
            for (int j = 0; j < 5; j++) {
                double dx = (level.random.nextDouble()-0.5)*1.2, dz = (level.random.nextDouble()-0.5)*1.2;
                level.addParticle(ParticleTypes.LAVA, pos.x+dx, pos.y+height, pos.z+dz, dx*0.15, 0.3+height*0.08, dz*0.15);
            }
            level.addParticle(ParticleTypes.FLAME, pos.x, pos.y+height, pos.z, 0, 0.5+height*0.05, 0);
            level.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y+height, pos.z, 0, 0.3+height*0.03, 0);
        }
        for (int r = 0; r < 4; r++) {
            float radius = 1.5f+r*1.2f;
            for (int i = 0; i < 20; i++) {
                float a = 2f*(float)Math.PI*i/20;
                level.addParticle(ParticleTypes.SMALL_FLAME, pos.x+Math.cos(a)*radius, pos.y+0.2, pos.z+Math.sin(a)*radius, Math.cos(a)*0.4, 0.1, Math.sin(a)*0.4);
                level.addParticle(ParticleTypes.LAVA, pos.x+Math.cos(a)*radius, pos.y+0.1, pos.z+Math.sin(a)*radius, Math.cos(a)*0.2, 0.3, Math.sin(a)*0.2);
            }
        }
        var p = Minecraft.getInstance().player;
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.5f, level.random.nextLong());
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 2.0f, 0.8f, level.random.nextLong());
        level.playSeededSound(p, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.5f, level.random.nextLong());
    }

    private static void spawnAttackParticlesGround(Level level, Vec3 pos) {
        int hx = Mth.floor(pos.x), hy = Mth.floor(pos.y)-1, hz = Mth.floor(pos.z);
        var bs = level.getBlockState(new BlockPos(hx, hy, hz));
        if (bs.getRenderShape() != RenderShape.INVISIBLE) {
            for (int i = 0; i < 20; i++) {
                double dx = (level.random.nextDouble()-0.5)*3, dz = (level.random.nextDouble()-0.5)*3;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, bs),
                    pos.x+dx, pos.y, pos.z+dz, dx*0.1, 0.2+level.random.nextDouble()*0.2, dz*0.1);
            }
        }
    }
}
