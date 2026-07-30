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
        }

        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean(FLIGHT_TAG)) {
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
