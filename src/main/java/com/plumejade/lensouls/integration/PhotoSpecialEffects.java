package com.plumejade.lensouls.integration;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.*;

public class PhotoSpecialEffects {

    private static final Set<String> FLYING_ENTITIES = Set.of(
            "twilightforest:ur_ghast", "twilightforest:snow_queen",
            "minecraft:bat", "minecraft:ender_dragon", "minecraft:wither", "minecraft:phantom"
    );
    private static final String FLIGHT_TAG = "lensouls:flight_photo";
    private static final String ATTR_TAG = "lensouls:attr_photos";
    private static final ResourceLocation MOD_BASE = ResourceLocation.parse("lensouls:attr_");

    private static final Map<String, DamageRule> DAMAGE_RULES = new HashMap<>();
    private record DamageRule(java.util.function.Predicate<LivingDamageEvent.Pre> matcher, float multiplier) {}

    static {
        DAMAGE_RULES.put("minecraft:creeper",  new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.8f));
        DAMAGE_RULES.put("minecraft:ghast",    new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.8f));
        DAMAGE_RULES.put("twilightforest:knight_phantom", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.75f));
        DAMAGE_RULES.put("cataclysm:ender_guardian", new DamageRule(e -> true, 0.7f));
        DAMAGE_RULES.put("cataclysm:netherite_monstrosity", new DamageRule(e -> e.getSource().is(DamageTypes.MOB_ATTACK) || e.getSource().is(DamageTypes.PLAYER_ATTACK), 0.85f));
        DAMAGE_RULES.put("cataclysm:scylla",  new DamageRule(e -> e.getSource().is(DamageTypes.MAGIC) || e.getSource().is(DamageTypes.INDIRECT_MAGIC), 0.8f));
        DAMAGE_RULES.put("legendary_monsters:the_obliterator", new DamageRule(e -> true, 0.9f));
        DAMAGE_RULES.put("legendary_monsters:skeletosaurus", new DamageRule(e -> e.getSource().is(DamageTypes.ARROW) || e.getSource().is(DamageTypes.TRIDENT), 0.7f));
        DAMAGE_RULES.put("legendary_monsters:ancient_guardian", new DamageRule(e -> e.getSource().is(DamageTypes.MAGIC) || e.getSource().is(DamageTypes.INDIRECT_MAGIC), 0.75f));
        DAMAGE_RULES.put("legendary_monsters:endersent", new DamageRule(e -> true, 0.5f));
        DAMAGE_RULES.put("legendary_monsters:annihilation_pursuer", new DamageRule(e -> e.getSource().is(DamageTypes.MOB_ATTACK) || e.getSource().is(DamageTypes.PLAYER_ATTACK), 0.8f));
        DAMAGE_RULES.put("twilightforest:alpha_yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        DAMAGE_RULES.put("minecraft:wither", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        DAMAGE_RULES.put("block_factorys_bosses:underworld_knight", new DamageRule(e -> e.getSource().is(DamageTypes.WITHER) || e.getSource().is(DamageTypes.WITHER_SKULL), 0.05f));
        DAMAGE_RULES.put("minecraft:ender_dragon", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        DAMAGE_RULES.put("legendary_monsters:cloud_golem", new DamageRule(e -> e.getSource().is(DamageTypes.FALL), 0.05f));
        DAMAGE_RULES.put("twilightforest:hydra", new DamageRule(e -> e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE), 0.7f));
        DAMAGE_RULES.put("cataclysm:ignis", new DamageRule(e -> e.getSource().is(DamageTypes.EXPLOSION) || e.getSource().is(DamageTypes.PLAYER_EXPLOSION), 0.7f));
        DAMAGE_RULES.put("legendary_monsters:frostbitten_golem", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
        DAMAGE_RULES.put("block_factorys_bosses:yeti", new DamageRule(e -> e.getSource().is(DamageTypes.FREEZE), 0.05f));
    }

    private static final Map<String, AttributeEntry> ATTRIBUTES = new HashMap<>();
    private record AttributeEntry(Attribute attribute, String modName, double amount, AttributeModifier.Operation operation) {}
    private static Holder<Attribute> holder(Attribute a) {
        return net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.wrapAsHolder(a);
    }

    static {
        ATTRIBUTES.put("minecraft:zombie",  new AttributeEntry(Attributes.MAX_HEALTH.value(), "zombie_photo", 4.0, AttributeModifier.Operation.ADD_VALUE));
        ATTRIBUTES.put("minecraft:skeleton", new AttributeEntry(Attributes.ATTACK_DAMAGE.value(), "skeleton_photo", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ATTRIBUTES.put("minecraft:spider",  new AttributeEntry(Attributes.JUMP_STRENGTH.value(), "spider_photo", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ATTRIBUTES.put("minecraft:ender_dragon", new AttributeEntry(Attributes.KNOCKBACK_RESISTANCE.value(), "dragon_photo", 1.0, AttributeModifier.Operation.ADD_VALUE));
        ATTRIBUTES.put("legendary_monsters:posessed_paladin", new AttributeEntry(Attributes.ARMOR.value(), "paladin_photo", 2.0, AttributeModifier.Operation.ADD_VALUE));
        ATTRIBUTES.put("legendary_monsters:posessed_paladin", new AttributeEntry(Attributes.KNOCKBACK_RESISTANCE.value(), "paladin_knockback", 1.0, AttributeModifier.Operation.ADD_VALUE));
        ATTRIBUTES.put("legendary_monsters:cloud_golem", new AttributeEntry(Attributes.JUMP_STRENGTH.value(), "golem_photo", 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ATTRIBUTES.put("legendary_monsters:frostbitten_golem", new AttributeEntry(Attributes.ARMOR.value(), "frost_photo", 3.0, AttributeModifier.Operation.ADD_VALUE));
        ATTRIBUTES.put("legendary_monsters:dune_sentinel", new AttributeEntry(Attributes.MOVEMENT_SPEED.value(), "dune_photo", 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean hasFlightPhoto = hasEntityInGear(player, FLYING_ENTITIES::contains);
        if (hasFlightPhoto) {
            player.getAbilities().mayfly = true;
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

        applyAttributes(player);

        // 末影人：装备照片时周围末影人不主动攻击玩家（节流到每 10 tick）
        if (player.tickCount % 10 == 0 && hasEntityInGear(player, id -> "minecraft:enderman".equals(id))) {
            player.level().getEntities(player, player.getBoundingBox().inflate(16.0),
                            e -> e instanceof net.minecraft.world.entity.monster.EnderMan)
                    .forEach(e -> {
                        net.minecraft.world.entity.monster.EnderMan em = (net.minecraft.world.entity.monster.EnderMan) e;
                        if (em.getTarget() == player) em.setTarget(null);
                    });
        }

        // 遍历所有 Curios 槽位应用照片效果
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                IDynamicStackHandler stackHandler = stacksHandler.getStacks();
                for (int i = 0; i < stackHandler.getSlots(); i++) {
                    ItemStack stack = stackHandler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    String stolen = PhotographEffectRegistry.getStolenEntity(stack);
                    if (stolen != null) PhotographEffectRegistry.applyEffects(player, stolen);
                }
            }
        });
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            for (var entry : DAMAGE_RULES.entrySet()) {
                if (hasEntityInGear(player, id -> id.equals(entry.getKey()))) {
                    DamageRule rule = entry.getValue();
                    if (rule.matcher().test(event) && rule.multiplier() < 1.0f) {
                        event.setNewDamage(event.getNewDamage() * rule.multiplier());
                        break;
                    }
                }
            }

            // 荆棘：受到近战攻击时概率反伤（仿原版 ThornsEnchantment：15% 概率反 1~4 点）
            if (hasEntityInGear(player, id -> "twilightforest:naga".equals(id))) {
                net.minecraft.world.entity.Entity attacker = event.getSource().getEntity();
                if (attacker instanceof LivingEntity le && attacker.distanceToSqr(player) < 16.0
                        && player.getRandom().nextFloat() < 0.15f) {
                    attacker.hurt(player.damageSources().thorns(player), 1.0f + player.getRandom().nextInt(4));
                }
            }
        }

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            // 飞行照片的伤害惩罚仅在飞行时生效，走地时正常伤害
            if (player.getPersistentData().getBoolean(FLIGHT_TAG) && player.getAbilities().flying) {
                event.setNewDamage(event.getOriginalDamage() * 0.1f);
            }
            if (hasEntityInGear(player, id -> "cataclysm:ignis".equals(id))) {
                if (player.getRandom().nextFloat() < 0.2f) {
                    try {
                        Class<?> mc = Class.forName("com.github.L_Ender.cataclysm.init.ModEffect");
                        var f = mc.getDeclaredField("EFFECTBLAZING_BRAND");
                        var d = f.get(null);
                        var h = (Holder<net.minecraft.world.effect.MobEffect>) d.getClass().getMethod("get").invoke(d);
                        var ex = event.getEntity().getEffect(h);
                        int a = ex != null ? Math.min(ex.getAmplifier() + 1, 2) : 0;
                        event.getEntity().addEffect(new net.minecraft.world.effect.MobEffectInstance(h, 100, a));
                    } catch (Exception ex) {
                        com.plumejade.lensouls.LenSouls.LOGGER.warn("[Photo] 炽焰烙印失效", ex);
                    }
                }
            }
        }
    }

    /** 防递归标志：witch 手动添加缩短版效果时跳过自身处理 */
    private static boolean handlingWitch = false;

    /** 灾变/传奇怪物注册的诅咒效果 ID（maledictus 诅咒免疫仅针对这些） */
    private static final Set<String> CURSE_EFFECTS = Set.of(
            "cataclysm:abyssal_curse",
            "cataclysm:curse_of_desert",
            "legendary_monsters:curse_of_desert"
    );

    /** 巫婆：负面效果时长 -50%；守卫者：黑暗免疫；诅咒魔：诅咒效果免疫 */
    @SubscribeEvent
    public static void onEffectApplicable(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        net.minecraft.world.effect.MobEffectInstance inst = event.getEffectInstance();
        if (inst == null) return;

        boolean beneficial = inst.getEffect().value().isBeneficial();
        String effectId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getKey(inst.getEffect().value()).toString();

        boolean warden = hasEntityInGear(player, id -> "minecraft:warden".equals(id));
        boolean maledictus = hasEntityInGear(player, id -> "cataclysm:maledictus".equals(id));
        boolean witch = hasEntityInGear(player, id -> "minecraft:witch".equals(id));

        if (warden && effectId.equals("minecraft:darkness")) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (hasEntityInGear(player, id -> "minecraft:cave_spider".equals(id)) && effectId.equals("minecraft:poison")) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (maledictus && CURSE_EFFECTS.contains(effectId)) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            return;
        }
        if (!handlingWitch && witch && !beneficial && inst.getDuration() > 1) {
            handlingWitch = true;
            try {
                event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        inst.getEffect(), inst.getDuration() / 2, inst.getAmplifier(),
                        inst.isAmbient(), inst.isVisible(), inst.showIcon()));
            } finally {
                handlingWitch = false;
            }
        }
    }

    /** 仿生傀儡：被弹射物命中时偏转（取消命中并反向弹射物） */
    @SubscribeEvent
    public static void onProjectileImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof ServerPlayer player
                && hasEntityInGear(player, id -> "legendary_monsters:shulker_mimic".equals(id))) {
            net.minecraft.world.phys.Vec3 vel = event.getProjectile().getDeltaMovement();
            if (vel.lengthSqr() > 0.001) {
                event.getProjectile().setDeltaMovement(vel.scale(-1.0));
            }
            event.setCanceled(true);
        }
    }

    private static void applyAttributes(ServerPlayer player) {
        Set<String> active = new HashSet<>();
        for (var entry : ATTRIBUTES.entrySet()) {
            if (hasEntityInGear(player, id -> id.equals(entry.getKey()))) active.add(entry.getKey());
        }
        String prev = player.getPersistentData().getString(ATTR_TAG);
        if (!prev.isEmpty()) {
            for (String id : prev.split(",")) {
                if (!active.contains(id)) {
                    AttributeEntry ae = ATTRIBUTES.get(id);
                    if (ae != null) {
                        AttributeInstance ai = player.getAttribute(holder(ae.attribute()));
                        if (ai != null) ai.removeModifier(MOD_BASE.withPath("photo_" + id.replace(':', '_')));
                    }
                }
            }
        }
        for (String id : active) {
            AttributeEntry ae = ATTRIBUTES.get(id);
            if (ae != null) {
                AttributeInstance ai = player.getAttribute(holder(ae.attribute()));
                var mid = MOD_BASE.withPath("photo_" + id.replace(':', '_'));
                if (ai != null && ai.getModifier(mid) == null)
                    ai.addTransientModifier(new AttributeModifier(mid, ae.amount(), ae.operation()));
            }
        }
        player.getPersistentData().putString(ATTR_TAG, String.join(",", active));
    }

    public static boolean hasEntityInGear(ServerPlayer player, java.util.function.Predicate<String> predicate) {
        var opt = CuriosApi.getCuriosInventory(player);
        if (opt.isEmpty()) return false;
        var handler = opt.get();
        for (var stacksHandler : handler.getCurios().values()) {
            IDynamicStackHandler stackHandler = stacksHandler.getStacks();
            for (int i = 0; i < stackHandler.getSlots(); i++) {
                ItemStack stack = stackHandler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                String entity = PhotographEffectRegistry.getStolenEntity(stack);
                if (entity != null && predicate.test(entity)) return true;
            }
        }
        return false;
    }
}
