package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class PhotographEffectRegistry {

    private static final Map<String, List<EffectEntry>> EFFECTS = new ConcurrentHashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new ConcurrentHashMap<>();

    public sealed interface EffectEntry permits PotionEffect, DamageEffect, SpecialEffect {}
    public record PotionEffect(Holder<MobEffect> effect, int amplifier) implements EffectEntry {}
    public record DamageEffect(BiConsumer<LivingEntity, Float> handler) implements EffectEntry {}
    public record SpecialEffect(BiConsumer<LivingEntity, String> handler) implements EffectEntry {}

    static {
        potion("minecraft:creeper",          "爆炸伤害 -20%%", null, 0);
        attr("minecraft:zombie",             "最大生命 +4");
        attr("minecraft:skeleton",           "远程伤害 +10%%");
        potion("minecraft:spider",           "跳跃提升 I", "minecraft:jump_boost", 0);
        spec("minecraft:enderman",           "末影人不主动攻击");
        potion("minecraft:blaze",            "火焰免疫", "minecraft:fire_resistance", 0);
        dmg("minecraft:ghast",               "爆炸伤害 -20%%", "minecraft:explosion");
        spec("minecraft:witch",              "受到的负面效果时长 -50%%");
        spec("minecraft:cave_spider",        "中毒免疫");
        spec("minecraft:warden",             "黑暗效果免疫");
        potion("minecraft:wither",           "凋零免疫", "minecraft:fire_resistance", 0);
        spec("minecraft:ender_dragon",       "击退抗性 + 摔落免疫");
        potion("twilightforest:hydra",       "火焰免疫 + 弹射物伤害 -30%%", "minecraft:fire_resistance", 0);
        dmg("twilightforest:knight_phantom", "受到亡灵伤害 -25%%", "minecraft:generic");
        potion("twilightforest:alpha_yeti",  "冰冻免疫", "minecraft:fire_resistance", 0);
        spec("twilightforest:naga",          "荆棘 I");
        potion("cataclysm:ignis",            "火焰免疫 + 爆炸伤害 -30%%", "minecraft:fire_resistance", 0);
        dmg("cataclysm:ender_guardian",      "末影伤害 -30%%", "minecraft:generic");
        dmg("cataclysm:netherite_monstrosity", "物理伤害 -15%%", "minecraft:generic");
        potion("cataclysm:the_leviathan",    "水下呼吸 + 游泳加速", "minecraft:water_breathing", 0);
        dmg("cataclysm:scylla",              "魔法伤害 -20%%", "minecraft:generic");
        spec("cataclysm:maledictus",         "诅咒免疫");
        potion("legendary_monsters:cloud_golem", "摔落免疫 + 跳跃提升 II", "minecraft:jump_boost", 1);
        attr("legendary_monsters:posessed_paladin", "击退抗性 + 护甲 +2");
        dmg("legendary_monsters:the_obliterator", "所有伤害 -10%%", "minecraft:generic");
        potion("legendary_monsters:lava_eater", "火焰免疫", "minecraft:fire_resistance", 0);
        potion("legendary_monsters:overgrown_colossus", "再生 I", "minecraft:regeneration", 0);
        spec("legendary_monsters:dune_sentinel", "移动速度 +10%%");
        dmg("legendary_monsters:skeletosaurus", "弓箭伤害 -30%%", "minecraft:generic");
        potion("legendary_monsters:frostbitten_golem", "冰冻免疫 + 护甲 +3", "minecraft:fire_resistance", 0);
        dmg("legendary_monsters:ancient_guardian", "魔法伤害 -25%%", "minecraft:generic");
        spec("legendary_monsters:shulker_mimic", "弹射物偏转");
        dmg("legendary_monsters:endersent", "末影伤害 -50%%", "minecraft:generic");
        dmg("legendary_monsters:annihilation_pursuer", "物理伤害 -20%%", "minecraft:generic");
        potion("block_factorys_bosses:yeti", "冰冻免疫 + 力量 I", "minecraft:strength", 0);
        potion("block_factorys_bosses:underworld_knight", "火焰免疫 + 凋零免疫", "minecraft:fire_resistance", 0);
        // 特殊效果
        spec("cataclysm:ignis",        "攻击时 20%% 概率施加炽焰烙印（防具 -20%%）");
        spec("cataclysm:the_leviathan", "获得海豚恩惠（水中加速）");
        spec("twilightforest:ur_ghast", "创造飞行（伤害 -90%%）");
        spec("twilightforest:snow_queen", "创造飞行（伤害 -90%%）");
        spec("minecraft:bat",          "创造飞行（伤害 -90%%）");
        spec("minecraft:ender_dragon", "创造飞行（伤害 -90%%）");
        spec("minecraft:wither",       "创造飞行（伤害 -90%%）");
        spec("minecraft:phantom",      "创造飞行（伤害 -90%%）");
    }

    private static void potion(String id, String desc, String effectId, int amp) {
        Holder<MobEffect> effect = effectId != null ?
                BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effectId)).orElse(null) : null;
        add(id, desc, effect != null ? new PotionEffect(effect, amp) : null);
    }

    private static void attr(String id, String desc) {
        add(id, desc, new SpecialEffect((e, s) -> {}));
    }

    private static void dmg(String id, String desc, String type) {
        add(id, desc, new SpecialEffect((e, s) -> {}));
    }

    private static void spec(String id, String desc) {
        add(id, desc, new SpecialEffect((e, s) -> {}));
    }

    private static void add(String entityId, String desc, EffectEntry entry) {
        EFFECTS.computeIfAbsent(entityId, k -> new ArrayList<>()).add(entry);
        DESCRIPTIONS.put(entityId, desc);
    }

    /** 每 tick 应用效果 */
    public static void applyEffects(LivingEntity player, String entityId) {
        List<EffectEntry> list = EFFECTS.get(entityId);
        if (list != null) {
            applyList(player, list);
            return;
        }
        // fallback: attacker_element 配置 → 动态生成元素活性效果
        var id = ResourceLocation.parse(entityId);
        if (com.plumejade.lensouls.config.AttackerElementLoader.hasMapping(id)) {
            ElementDamage elem = com.plumejade.lensouls.config.AttackerElementLoader.getElement(id);
            if (elem != null) {
                Holder<MobEffect> effect = switch (elem) {
                    case FIRE -> com.plumejade.lensouls.effect.ModEffects.FIRE_INFUSION;
                    case WATER -> com.plumejade.lensouls.effect.ModEffects.WATER_INFUSION;
                    case EARTH -> com.plumejade.lensouls.effect.ModEffects.EARTH_INFUSION;
                    case ENDER -> com.plumejade.lensouls.effect.ModEffects.ENDER_INFUSION;
                    default -> null;
                };
                if (effect != null) {
                    var inst = player.getEffect(effect);
                    if (inst == null || inst.getDuration() < 100) {
                        player.addEffect(new MobEffectInstance(effect, 300, 0, false, false, true));
                    }
                }
            }
        }
    }

    private static void applyList(LivingEntity player, List<EffectEntry> list) {
        for (EffectEntry e : list) {
            switch (e) {
                case PotionEffect p -> {
                    var inst = player.getEffect(p.effect());
                    if (inst == null || inst.getDuration() < 100) {
                        player.addEffect(new MobEffectInstance(p.effect(), 300, p.amplifier(), false, false, true));
                    }
                }
                case SpecialEffect s -> s.handler().accept(player, "dummy");
                case DamageEffect d -> {}
            }
        }
    }

    public static String getStolenEntity(ItemStack stack) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        return tag.contains("lensouls:stolen_entity") ? tag.getString("lensouls:stolen_entity") : null;
    }

    public static boolean hasEffect(String entityId) {
        return entityId != null && EFFECTS.containsKey(entityId);
    }

    public static Set<String> getAllEntityIds() {
        return EFFECTS.keySet();
    }

    public static String entityIdToTranslationKey(String entityId) {
        int colon = entityId.indexOf(':');
        if (colon < 0) return "entity." + entityId;
        return "entity." + entityId.substring(0, colon) + "." + entityId.substring(colon + 1);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        String entityId = getStolenEntity(stack);
        if (entityId == null) return;

        event.getToolTip().add(Component.translatable("lensouls.photograph.slot_hint").withStyle(ChatFormatting.YELLOW));
        event.getToolTip().add(Component.translatable("lensouls.photograph.entity_name",
                Component.translatable(entityIdToTranslationKey(entityId))));

        // 硬编码效果描述
        String desc = DESCRIPTIONS.get(entityId);
        if (desc != null) {
            event.getToolTip().add(Component.literal("§7" + desc));
            return;
        }
        // attacker_element fallback → 元素活性 I
        var id = ResourceLocation.parse(entityId);
        if (com.plumejade.lensouls.config.AttackerElementLoader.hasMapping(id)) {
            ElementDamage elem = com.plumejade.lensouls.config.AttackerElementLoader.getElement(id);
            if (elem != null)
                event.getToolTip().add(Component.literal("")
                        .append(Component.translatable("element.lensouls." + elem.getSerializedName() + ".short"))
                        .append(Component.literal(" 活性 I"))
                        .withStyle(ChatFormatting.GREEN));
        }
    }
}
