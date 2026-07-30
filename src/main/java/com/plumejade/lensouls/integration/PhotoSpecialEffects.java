package com.plumejade.lensouls.integration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

/**
 * 照片特殊效果处理器：
 * - 焰魔 → 概率施加炽焰烙印
 * - 飞行类实体 → 创造飞行 + 伤害-90%
 */
public class PhotoSpecialEffects {

    private static final Set<String> FLYING_ENTITIES = Set.of(
            "twilightforest:ur_ghast", "twilightforest:snow_queen",
            "minecraft:bat", "minecraft:ender_dragon", "minecraft:wither", "minecraft:phantom"
    );
    private static final String FLIGHT_TAG = "lensouls:flight_photo";

    /** 每 tick 维护飞行状态 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean hasFlightPhoto = hasEntityInGear(player, FLYING_ENTITIES::contains);
        if (hasFlightPhoto) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = player.getAbilities().flying || player.getAbilities().mayfly;
            player.onUpdateAbilities();
            player.getPersistentData().putBoolean(FLIGHT_TAG, true);
        } else if (player.getPersistentData().getBoolean(FLIGHT_TAG)) {
            player.getPersistentData().remove(FLIGHT_TAG);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    /** 攻击时：焰魔→炽焰烙印；飞行照片→伤害-90% */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        // 飞行照片 → 伤害-90%
        if (player.getPersistentData().getBoolean(FLIGHT_TAG)) {
            event.setNewDamage(event.getOriginalDamage() * 0.1f);
        }

        // 焰魔 → 概率炽焰烙印（叠加：每触发+1级，最高III）
        if (hasEntityInGear(player, id -> "cataclysm:ignis".equals(id))) {
            if (player.getRandom().nextFloat() < 0.2f) {
                LivingEntity target = event.getEntity();
                try {
                    Class<?> modEffectClass = Class.forName("com.github.L_Ender.cataclysm.init.ModEffect");
                    var field = modEffectClass.getDeclaredField("EFFECTBLAZING_BRAND");
                    var holder = (net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>) field.get(null);
                    var existing = target.getEffect(holder);
                    int amp = existing != null ? Math.min(existing.getAmplifier() + 1, 2) : 0;
                    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(holder, 100, amp));
                } catch (Exception ignored) {}
            }
        }
    }

    /** 检查玩家 Curios head 槽中是否有满足条件的照片 */
    private static boolean hasEntityInGear(ServerPlayer player, java.util.function.Predicate<String> predicate) {
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            var getCurios = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCurios.invoke(null, player);
            if (!(boolean) optional.getClass().getMethod("isPresent").invoke(optional)) return false;
            Object handler = optional.getClass().getMethod("get").invoke(optional);
            var results = (java.util.List<?>) handler.getClass().getMethod("findCurios", String.class).invoke(handler, "head");
            if (results == null) return false;
            for (Object r : results) {
                ItemStack stack = (ItemStack) r.getClass().getMethod("stack").invoke(r);
                String entity = PhotographEffectRegistry.getStolenEntity(stack);
                if (entity != null && predicate.test(entity)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
