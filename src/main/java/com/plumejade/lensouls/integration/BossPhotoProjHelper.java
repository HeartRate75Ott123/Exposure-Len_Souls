package com.plumejade.lensouls.integration;

import com.mojang.math.Axis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss 照片弹幕触发框架。
 * <p>
 * 触发信号由 mixin 提供（{@code Player#attack} / BetterCombat {@code ServerNetwork#handleAttackRequest}），
 * 每次完整挥砍开始调用 {@link #onSwing}。空手狂按 / 空挥均触发（BetterCombat 环境）。
 * 发射：玩家位置 + 玩家视线方向。所有弹射物以玩家为 shooter/caster（伤害归属玩家，不伤自身/队友）。
 */
public class BossPhotoProjHelper {

    /** 挥击去重（内存态，不持久化——persistentData 跨会话污染会导致永不触发） */
    private static final java.util.Map<java.util.UUID, Integer> LAST_SWING =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** boss 照片 id → 触发概率 */
    private static final Map<String, Float> TRIGGER = new HashMap<>();
    static {
        TRIGGER.put("cataclysm:ender_guardian", 0.15f);
        TRIGGER.put("cataclysm:ignis", 0.15f);
        TRIGGER.put("cataclysm:netherite_monstrosity", 0.12f);
        TRIGGER.put("cataclysm:the_harbinger", 0.12f);
        TRIGGER.put("cataclysm:the_leviathan", 0.10f);
        TRIGGER.put("cataclysm:ancient_remnant", 0.12f);
        TRIGGER.put("cataclysm:maledictus", 0.15f);
        TRIGGER.put("cataclysm:scylla", 0.15f);
        TRIGGER.put("legendary_monsters:posessed_paladin", 0.15f);
        TRIGGER.put("legendary_monsters:cloud_golem", 0.10f);
    }

    /** 每次完整挥砍开始调用（由 Player#attack / BetterCombat handleAttackRequest mixin 触发） */
    public static void onSwing(ServerPlayer player) {
        // 去重：BetterCombat 命中时会同时走原版 attack 与 handleAttackRequest，3 tick 内只触发一次
        Integer last = LAST_SWING.get(player.getUUID());
        if (last != null && player.tickCount - last < 3) return;
        LAST_SWING.put(player.getUUID(), player.tickCount);

        List<String> gear = PhotoSpecialEffects.collectGearEntities(player);
        com.plumejade.lensouls.LenSouls.LOGGER.info("[PhotoBoss] swing tick={} photos={}", player.tickCount, gear);
        for (String id : gear) {
            Float chance = TRIGGER.get(id);
            if (chance != null && player.getRandom().nextFloat() < chance) {
                com.plumejade.lensouls.LenSouls.LOGGER.info("[PhotoBoss] trigger {} chance={}", id, chance);
                fireBossSkill(player, id);
            }
        }
    }

    // ========== 各 Boss 弹幕 ==========

    private static void fireBossSkill(ServerPlayer player, String bossId) {
        try {
            switch (bossId) {
                case "cataclysm:ender_guardian" -> spawnVoidRune(player);
                case "cataclysm:ignis" -> spawnIgnisFireballs(player);
                case "cataclysm:netherite_monstrosity" -> spawnFallingBlocks(player);
                case "cataclysm:the_harbinger" -> spawnLaserBeam(player);
                case "cataclysm:the_leviathan" -> spawnAbyssBlast(player);
                case "cataclysm:ancient_remnant" -> spawnDesertStele(player);
                case "cataclysm:maledictus" -> spawnPhantomArrows(player);
                case "cataclysm:scylla" -> spawnWaves(player);
                case "legendary_monsters:posessed_paladin" -> spawnSoulPillars(player);
                case "legendary_monsters:cloud_golem" -> spawnEnergyBeam(player);
            }
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.warn("[PhotoBoss] 弹幕触发失败: " + bossId, e);
        }
    }

    /** 末影守卫：虚空符文（1.5s 后引爆，6 点魔法伤害）——召唤在最近的非玩家生物脚下 */
    private static void spawnVoidRune(ServerPlayer player) {
        Level level = player.level();
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        double px, pz, py;
        LivingEntity target = findNearestNonPlayer(player, 16.0);
        if (target != null) {
            px = target.getX();
            pz = target.getZ();
            py = findGroundY(level, px, target.getY(), pz);
        } else {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            px = eye.x + look.x * 4.0;
            pz = eye.z + look.z * 4.0;
            py = findGroundY(level, px, eye.y, pz);
        }
        if (py < level.getMinBuildHeight() + 1) return;
        var rune = new com.github.L_Ender.cataclysm.entity.projectile.Void_Rune_Entity(
                level, px, py, pz, yawRad, 30, 6.0f, player);
        markAndSpawn(rune);
    }

    /** 玩家周围指定范围内最近的非玩家 LivingEntity（排除玩家自身） */
    private static LivingEntity findNearestNonPlayer(ServerPlayer player, double radius) {
        LivingEntity best = null;
        double bestDist = radius * radius;
        for (net.minecraft.world.entity.Entity e : player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                en -> !(en instanceof net.minecraft.world.entity.player.Player) && en.isAlive())) {
            double d = player.distanceToSqr(e);
            if (d < bestDist) {
                bestDist = d;
                best = (LivingEntity) e;
            }
        }
        return best;
    }

    /** 焰魔：烈焰轰击 ×3（5+5%生命，命中施加炽焰烙印） */
    private static void spawnIgnisFireballs(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        for (int i = 0; i < 3; i++) {
            var fb = new com.github.L_Ender.cataclysm.entity.projectile.Ignis_Fireball_Entity(level, player);
            fb.setPos(player.getX() + look.x * 0.5, player.getEyeY(), player.getZ() + look.z * 0.5);
            // 扇形微偏
            double spread = (i - 1) * 0.12;
            Vec3 dir = look.yRot((float) spread);
            fb.shoot(dir.x, dir.y, dir.z, 0.25f, 3.0f);
            try { fb.setUp(0); } catch (Exception ignored) {}
            markAndSpawn(fb);
        }
    }

    /** 下界合金巨兽：掀地落石 ×3（5 点伤害 + 击飞） */
    private static void spawnFallingBlocks(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        BlockState block = Blocks.NETHERRACK.defaultBlockState();
        for (int i = 0; i < 3; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 3.0;
            double oz = (level.random.nextDouble() - 0.5) * 3.0;
            double x = player.getX() + look.x * 3.0 + ox;
            double z = player.getZ() + look.z * 3.0 + oz;
            double y = player.getEyeY() + 6.0;
            var fb = new com.github.L_Ender.cataclysm.entity.effect.Cm_Falling_Block_Entity(level, x, y, z, block, 20);
            fb.setPos(x, y, z);
            fb.push(0, -0.5 + level.random.nextDouble() * 0.2, 0);
            markAndSpawn(fb);
        }
    }

    /** 先驱者：凋零激光束（4 点伤害 + 点燃 5s） */
    private static void spawnLaserBeam(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        var beam = new com.github.L_Ender.cataclysm.entity.projectile.Laser_Beam_Entity(player, look, level, 4.0f);
        beam.setPos(player.getX(), player.getEyeY(), player.getZ());
        markAndSpawn(beam);
    }

    /** 利维坦：深渊裂缝激光（8+5%生命，2s 后从裂缝射出） */
    private static void spawnAbyssBlast(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double px = eye.x + look.x * 5.0;
        double pz = eye.z + look.z * 5.0;
        double py = findGroundY(level, px, eye.y, pz);
        if (py < level.getMinBuildHeight() + 1) return;
        float yaw = (float) (player.getYRot() * Math.PI / 180.0);
        var blast = new com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.The_Leviathan.Abyss_Blast_Portal_Entity(
                level, px, py, pz, yaw, 40, 8.0f, 0.05f, player);
        markAndSpawn(blast);
    }

    /** 远古遗魂：岩碑阵 ×3（8 点魔法伤害） */
    private static void spawnDesertStele(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        for (int i = 0; i < 3; i++) {
            double side = (i - 1) * 1.5;
            double px = eye.x + look.x * 4.0 - look.z * side;
            double pz = eye.z + look.z * 4.0 + look.x * side;
            double py = findGroundY(level, px, eye.y, pz);
            if (py < level.getMinBuildHeight() + 1) continue;
            var stele = new com.github.L_Ender.cataclysm.entity.projectile.Ancient_Desert_Stele_Entity(
                    level, px, py, pz, yawRad, 30, 8.0f, player);
            markAndSpawn(stele);
        }
    }

    /** 咒翼灵骸：追踪灵魂箭 ×3（3.5 点幽灵伤害） */
    private static void spawnPhantomArrows(ServerPlayer player) {
        Level level = player.level();
        Vec3 look = player.getViewVector(1.0F);
        LivingEntity target = player.getLastHurtMob();
        for (int i = 0; i < 3; i++) {
            var arrow = new com.github.L_Ender.cataclysm.entity.projectile.Phantom_Arrow_Entity(level, player, target);
            arrow.setBaseDamage(3.5f);
            double spread = (i - 1) * (6.0 * Math.PI / 180.0);
            Vec3 dir = look.yRot((float) spread);
            arrow.shoot(dir.x, dir.y, dir.z, 1.8f, 1.0f);
            arrow.setPos(player.getX(), player.getEyeY(), player.getZ());
            markAndSpawn(arrow);
        }
    }

    /** 斯库拉：水波 ×3（6 点伤害 + 湿润，扇形 25°） */
    private static void spawnWaves(ServerPlayer player) {
        Level level = player.level();
        for (int i = 0; i < 3; i++) {
            var wave = new com.github.L_Ender.cataclysm.entity.effect.Wave_Entity(level, player, 60, 6.0f);
            wave.setPos(player.getX(), player.getY() + 0.5, player.getZ());
            wave.setState(1);
            float yaw = player.getYRot() + (i - 1) * 25.0f;
            wave.setYRot(yaw);
            markAndSpawn(wave);
        }
    }

    /** 堕落圣骑：灵魂尖刺 ×3（6+3%生命，幽灵伤害） */
    private static void spawnSoulPillars(ServerPlayer player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        float yawRad = (float) (player.getYRot() * Math.PI / 180.0);
        for (int i = 0; i < 3; i++) {
            double side = (i - 1) * 1.25;
            double px = eye.x + look.x * 4.0 - look.z * side;
            double pz = eye.z + look.z * 4.0 + look.x * side;
            double py = findGroundY(level, px, eye.y, pz);
            if (py < level.getMinBuildHeight() + 1) continue;
            var pillar = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SoulPillarEntity(
                    level, px, py, pz, yawRad, 20, player, 20, 6.0f, false);
            markAndSpawn(pillar);
        }
    }

    /** 云筑魔像：天穹激光（贯穿 30 格，每 0.25s 3+1%生命，40tick） */
    private static void spawnEnergyBeam(ServerPlayer player) {
        Level level = player.level();
        EntityType<?> beamType = null;
        try {
            beamType = net.miauczel.legendary_monsters.entity.ModEntities.ENERGY_BEAM.get();
        } catch (Exception ignored) {}
        if (beamType == null) return;
        float yaw = (float) ((player.getYRot() + 90.0) * Math.PI / 180.0);
        float pitch = (float) (-player.getXRot() * Math.PI / 180.0);
        var beam = new net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.EnergyBeamEntity(
                (EntityType<? extends net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.EnergyBeamEntity>) beamType,
                level, player, player.getX(), player.getY() + 1.0, player.getZ(),
                yaw, pitch, 40, 3.0f, 0.0f);
        markAndSpawn(beam);
    }

    /** 从 y 向下扫描，返回地面稳固方块上方 1 格（找不到返回极小值） */
    private static double findGroundY(Level level, double x, double startY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = (int) Math.floor(startY) + 2; y > level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)) {
                return y + 1.0;
            }
        }
        return level.getMinBuildHeight() - 1;
    }

    /** 打上照片弹幕标记后加入世界（供 BossProjHurtMixin 清目标无敌帧） */
    private static void markAndSpawn(net.minecraft.world.entity.Entity entity) {
        entity.getPersistentData().putBoolean("lensouls:photo_proj", true);
        entity.level().addFreshEntity(entity);
    }
}
