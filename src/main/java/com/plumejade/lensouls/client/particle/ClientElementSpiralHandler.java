package com.plumejade.lensouls.client.particle;

import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端元素增伤爆发粒子 — 从受击实体中心向外爆炸扩散。
 */
public class ClientElementSpiralHandler {

    private static final ClientElementSpiralHandler INSTANCE = new ClientElementSpiralHandler();

    public static ClientElementSpiralHandler getInstance() { return INSTANCE; }

    public void startSpiral(int entityId, int elementOrdinal, boolean weaknessLens) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;

        ElementDamage element = null;
        if (!weaknessLens) {
            ElementDamage[] elements = ElementDamage.values();
            if (elementOrdinal >= 0 && elementOrdinal < elements.length)
                element = elements[elementOrdinal];
        }

        SimpleParticleType type = getParticleType(element, weaknessLens);
        var random = level.random;
        double cx = le.getX(), cy = le.getY() + le.getBbHeight() * 0.5, cz = le.getZ();

        for (int i = 0; i < 5; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;
            double speed = 0.15 + random.nextDouble() * 0.25;

            double vx = Math.sin(phi) * Math.cos(theta) * speed;
            double vy = Math.cos(phi) * speed * 0.6;
            double vz = Math.sin(phi) * Math.sin(theta) * speed;

            level.addParticle(type, cx, cy, cz, vx, vy, vz);
        }
    }

    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // no-op
    }

    private SimpleParticleType getParticleType(ElementDamage element, boolean weaknessLens) {
        if (weaknessLens)
            return com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_WEAKNESS.get();
        if (element == null)
            return com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_FIRE.get();
        return switch (element) {
            case FIRE -> com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_FIRE.get();
            case WATER -> com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_WATER.get();
            case EARTH -> com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_EARTH.get();
            case ENDER -> com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_ENDER.get();
            case PROJECTILE -> com.plumejade.lensouls.particle.ModParticleTypes.ELEMENT_SPIRAL_PROJECTILE.get();
        };
    }
}
