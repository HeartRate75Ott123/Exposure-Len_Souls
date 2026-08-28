package com.plumejade.lensouls.integration;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.config.AttackerElementLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 照片饰品效果注册表（描述层 + 药水层）。
 * <p>
 * 职责：
 * <ul>
 *   <li>集中维护每张照片的 tooltip 描述（§a 正面 / §c 负面，多行）</li>
 *   <li>注册常驻药水效果（potion）</li>
 *   <li>按 {@code attacker_element} 数据包给佩戴者赋予元素活性灌注（等级与数据包匹配）</li>
 *   <li>动态生成元素活性 tooltip（数据包改动后自动跟随）</li>
 * </ul>
 * 属性 / 减伤 / 免疫 / 特殊机制的实际逻辑在 {@link PhotoSpecialEffects}。
 */
public class PhotographEffectRegistry {

    /** entityId -> 描述行列表（每行可带 §a/§c/§7） */
    private static final Map<String, List<String>> DESCRIPTIONS = new ConcurrentHashMap<>();

    /** entityId -> 常驻药水条目 */
    private static final Map<String, List<PotionEntry>> POTIONS = new ConcurrentHashMap<>();

    public record PotionEntry(Holder<MobEffect> effect, int amplifier) {}

    // ========== 注册 API ==========

    /** 注册描述（多行，每行独立渲染，§ 颜色码生效） */
    private static void add(String entityId, String... lines) {
        List<String> list = DESCRIPTIONS.computeIfAbsent(entityId, k -> new ArrayList<>());
        Collections.addAll(list, lines);
    }

    /** 注册描述 + 一个常驻药水效果 */
    private static void potion(String entityId, String effectId, int amplifier, String... lines) {
        add(entityId, lines);
        BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(effectId)).ifPresent(h ->
                POTIONS.computeIfAbsent(entityId, k -> new ArrayList<>()).add(new PotionEntry(h, amplifier)));
    }

    /** 注册描述 + 一个元素灌注（等级从数据包动态读取，这里只声明元素） */
    private static void infusion(String entityId, String... lines) {
        add(entityId, lines);
    }

    // ========== 效果数据（描述与 PhotoSpecialEffects 逻辑一一对应） ==========

    static {
        // ── Boss 十连（弹幕 + 被动） ──
        add("cataclysm:ender_guardian",
                "§a挥击时 15% 概率在视线前方地面升起 虚空符文",
                "§a虚空符文：1.5 秒后引爆；对范围内目标造成 等同攻击面板 的魔法伤害",
                "§a佩戴时受到的所有来源伤害 -15%");
        add("cataclysm:ignis",
                "§a挥击时 15% 概率连发 3 颗 烈焰轰击（不破坏方块）",
                "§a烈焰轰击：命中造成 5 点 + 目标最大生命 5% 的火焰伤害",
                "§a烈焰轰击命中时赋予目标 炽焰烙印2 效果（防具/韧性 -20%）",
                "§a挥击时 20% 概率直接赋予目标 炽焰烙印1 效果",
                "§a佩戴时赋予 火焰免疫 效果");
        add("cataclysm:netherite_monstrosity",
                "§a挥击时 12% 概率从目标头顶降下 3 块落石",
                "§a落石：落地造成 等同攻击面板 的伤害并小幅击飞目标",
                "§a佩戴时受到的近战伤害 -12%");
        add("cataclysm:the_harbinger",
                "§a挥击时 12% 概率向视线方向发射 凋零激光束",
                "§a凋零激光束：命中造成 等同攻击面板 的伤害并点燃目标 5 秒",
                "§a佩戴时受到的凋零伤害 -50%",
                "§a佩戴时远程攻击伤害 +12%",
                "§c佩戴时近战伤害 -12%");
        add("cataclysm:the_leviathan",
                "§a挥击时 10% 概率在视线前方地面裂开 深渊裂缝",
                "§a深渊裂缝：2 秒后射出激光柱；造成 等同攻击面板 + 目标最大生命 5% 的魔法伤害",
                "§a佩戴时赋予 水下呼吸 效果",
                "§a被命中时 20% 概率获得 海豚恩惠3 效果（持续 5 秒）",
                "");
        add("cataclysm:ancient_remnant",
                "§a挥击时 12% 概率在目标脚下升起 岩碑阵（3 根；一字排开）",
                "§a岩碑：1.5 秒后炸裂；对范围内目标造成 等同攻击面板 的魔法伤害",
                "§a佩戴时受到的近战伤害 -12%");
        add("cataclysm:maledictus",
                "§a挥击时 15% 概率朝视线方向散射 3 支 追踪灵魂箭",
                "§a灵魂箭：追踪目标；每支造成 等同攻击面板 的幽灵伤害",
                "§a佩戴时免疫 灾变/传奇怪物 的诅咒类减益效果",
                "§a佩戴时受到的魔法伤害 -12%");
        add("cataclysm:scylla",
                "§a挥击时 15% 概率掀起 3 道水波（扇形扩散）",
                "§a水波：命中造成 等同攻击面板 的伤害、赋予 湿润1 效果并扑灭目标身上的火",
                "§a佩戴时受到的魔法伤害 -12%",
                "");
        add("legendary_monsters:posessed_paladin",
                "§a挥击时 15% 概率在目标脚下逐排升起 3 根灵魂尖刺",
                "§a灵魂尖刺：命中造成 等同攻击面板 + 目标最大生命 3% 的幽灵伤害",
                "§a灵魂尖刺命中时赋予目标 灵魂碎裂 效果（持续 4 秒）",
                "§a佩戴时赋予 抗性提升1 效果",
                "§c佩戴时近战伤害 -12%");
        add("legendary_monsters:cloud_golem",
                "§a挥击时 10% 概率从胸口射出 天穹激光",
                "§a天穹激光：贯穿视线方向 30 格；每 0.25 秒造成 等同攻击面板 + 目标最大生命 1% 的伤害",
                "§a佩戴时免疫 摔落 伤害",
                "§a佩戴时赋予 跳跃提升2 效果",
                "§c佩戴时受到的所有来源伤害 +12%");

        // ── 原版 战斗类 ──
        add("minecraft:zombie", "", "");
        add("minecraft:husk", "§a免疫 饥饿 效果", "");
        add("minecraft:drowned", "§a赋予 水下呼吸 效果；§a水下攻击 +10%", "");
        add("minecraft:skeleton", "§a远程攻击 +10%", "§a挥击时 15% 概率朝视线方向射出 3 支弓箭", "§a弓箭：每支造成 等同攻击面板 的伤害");
        add("minecraft:stray", "§a免疫 冰冻 伤害；§a远程攻击 +10%", "");
        add("minecraft:bogged", "§a免疫 中毒 效果；§a远程攻击 +10%", "");
        add("minecraft:creeper", "§a免疫 爆炸 伤害；§a受到所有来源伤害 -10%", "");
        add("minecraft:enderman", "§a末影人不主动攻击；§a免疫 末影珍珠 伤害", "§c你受到的 魔法 伤害 +12%");
        add("minecraft:endermite", "§a你受到的魔法伤害 -12%；§a免疫 末影珍珠 伤害", "");
        add("minecraft:blaze", "§a赋予 火焰免疫 效果", "§c你受到的 冰冻 伤害 +12%");
        add("minecraft:magma_cube", "§a赋予 火焰免疫 效果；§a免疫 摔落 伤害", "§c你受到的 冰冻 伤害 +12%");
        add("minecraft:ghast", "§a免疫 爆炸 伤害；§a你受到的爆炸伤害 -20%", "§c你受到的 火 伤害 +12%");
        add("minecraft:witch", "§a负面效果持续时间 -50%；§a药水效果时长 +20%", "");
        add("minecraft:cave_spider", "§a免疫 中毒 效果", "§c你受到的近战伤害 +5%");
        add("minecraft:spider", "", "§c你受到的近战伤害 +5%");
        add("minecraft:silverfish", "", "");
        add("minecraft:warden", "§a免疫 黑暗 效果；§a近战攻击 +15%", "");
        add("minecraft:wither", "§a免疫 凋零 效果；§a远程攻击 +10%", "§c你受到的治疗量 -20%");
        add("minecraft:wither_skeleton", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("minecraft:skeleton_horse", "", "");
        add("minecraft:zombie_horse", "", "");
        add("minecraft:slime", "§a免疫 摔落 伤害", "");
        add("minecraft:zombified_piglin", "§a赋予 火焰免疫 效果", "§c你受到的魔法伤害 +10%");
        add("minecraft:piglin", "§a赋予 火焰免疫 效果", "§c你受到的魔法伤害 +10%");
        add("minecraft:piglin_brute", "§a近战攻击 +15%", "");
        add("minecraft:hoglin", "§a近战攻击 +15%", "§c你受到的魔法伤害 +12%");
        add("minecraft:zoglin", "§a近战攻击 +10%", "§c你受到的魔法伤害 +12%");
        add("minecraft:strider", "§a赋予 火焰免疫 效果；§a免疫 熔岩 伤害；§a移动 +5%", "");
        add("minecraft:ravager", "§a近战攻击 +15%", "");
        add("minecraft:vindicator", "§a佩戴时额外获得 1 个照片饰品栏位", "");
        add("minecraft:evoker", "§a魔法攻击 +15%；§a你受到的魔法伤害 -12%", "§c近战伤害 -12%", "§a挥击时 15% 概率在视线前方钻出 一排尖牙", "§a尖牙：对范围内目标造成 魔法伤害（原版固定值）");
        add("minecraft:pillager", "§a远程攻击 +10%", "");
        add("minecraft:vex", "§a移动 +15%", "");
        add("minecraft:shulker", "", "");
        add("minecraft:guardian", "§a赋予 水下呼吸 效果", "");
        add("minecraft:elder_guardian", "§a赋予 海豚恩惠3 效果；§a游泳 +30%", "");

        // ── 原版 动物/中立 ──
        add("minecraft:pig", "§a每秒恢复 0.5 点 饥饿值", "");
        add("minecraft:cow", "§a赋予 再生1 效果", "");
        add("minecraft:mooshroom", "§a赋予 再生1 效果", "");
        add("minecraft:sheep", "§a免疫 细雪 减速效果", "");
        add("minecraft:chicken", "§a免疫 摔落 伤害", "");
        add("minecraft:rabbit", "", "");
        add("minecraft:wolf", "§a近战攻击 +10%；§a移动 +5%", "");
        add("minecraft:fox", "§a移动 +10%", "§c受到所有来源伤害 +5%");
        add("minecraft:cat", "§a周围 8 格内的 爬行者/幻翼 不会靠近", "");
        add("minecraft:ocelot", "§a移动 +10%", "");
        add("minecraft:parrot", "§a跳跃提升 + 免疫 摔落 伤害", "");
        add("minecraft:bat", "§a创造飞行（飞行时造成的伤害 -90%）", "");
        add("minecraft:bee", "§a移动 +10%", "");
        add("minecraft:allay", "§a 8 格内的掉落物自动吸向玩家", "");
        add("minecraft:armadillo", "", "");
        add("minecraft:turtle", "§a赋予 水下呼吸 效果", "");
        add("minecraft:axolotl", "§a赋予 水下呼吸 效果；§a水下受到伤害 -10%", "");
        add("minecraft:dolphin", "§a赋予 海豚恩惠3 效果；§a游泳 +30%", "");
        add("minecraft:pufferfish", "", "");
        add("minecraft:squid", "§a赋予 水下呼吸 效果；§a游泳 +10%", "");
        add("minecraft:glow_squid", "§a赋予 水下呼吸 效果；§a游泳 +10%", "");
        add("minecraft:goat", "", "");
        add("minecraft:horse", "§a移动 +10%", "");
        add("minecraft:donkey", "§a移动 +10%", "");
        add("minecraft:mule", "§a移动 +10%", "");
        add("minecraft:polar_bear", "§a近战攻击 +10%", "");
        add("minecraft:panda", "", "");
        add("minecraft:snow_golem", "§a免疫 冰冻 伤害", "§c你受到的 火 伤害 +12%");
        add("minecraft:iron_golem", "§a免疫 箭矢/弹射物 伤害", "");
        add("minecraft:llama", "", "");
        add("minecraft:trader_llama", "", "");
        add("minecraft:phantom", "§a创造飞行（飞行时造成的伤害 -90%）", "");
        add("minecraft:cod", "§a赋予 水下呼吸 效果；§a游泳 +10%", "");
        add("minecraft:salmon", "§a赋予 水下呼吸 效果；§a游泳 +10%", "");
        add("minecraft:tropical_fish", "§a赋予 水下呼吸 效果；§a游泳 +10%", "");
        add("minecraft:tadpole", "§a游泳 +15%", "");
        add("minecraft:frog", "", "");
        add("minecraft:camel", "§a移动 +5%", "");
        add("minecraft:sniffer", "", "");
        add("minecraft:villager", "§a交易价格优惠 +10%", "");
        add("minecraft:wandering_trader", "§a移动 +10%", "");

        // ── 暮色森林（重点） ──
        add("twilightforest:hydra", "§a赋予 火焰免疫 效果；§a你受到的远程伤害 -12%", "§c你受到的 冰冻 伤害 +12%");
        add("twilightforest:alpha_yeti", "§a免疫 冰冻 伤害；§a近战攻击 +10%", "");
        add("twilightforest:naga", "§a荆棘（受近战攻击反伤）", "");
        add("twilightforest:snow_queen", "§a创造飞行（飞行时造成的伤害 -90%）", "§c你受到的 火 伤害 +12%");
        add("twilightforest:ur_ghast", "§a创造飞行（飞行时造成的伤害 -90%）", "");
        add("twilightforest:lich", "§a魔法攻击 +12%；§a你受到的魔法伤害 -12%", "§c近战伤害 -12%");
        add("twilightforest:minotaur", "§a近战攻击 +15%", "");
        add("twilightforest:troll", "§a近战攻击 +15%", "");
        add("twilightforest:wraith", "§a移动 +10%；§a潜行速度 +20%", "");
        add("twilightforest:yeti", "§a免疫 冰冻 伤害；§a近战攻击 +10%", "");
        add("twilightforest:knight_phantom", "§a你受到的凋零伤害 -25%", "");
        add("twilightforest:armored_giant", "", "");
        add("twilightforest:death_tome", "§a魔法攻击 +10%", "");
        add("twilightforest:minoshroom", "§a近战攻击 +15%", "");
        add("twilightforest:helmet_crab", "", "");
        add("twilightforest:quest_ram", "", "");
        add("twilightforest:squirrel", "", "");
        add("twilightforest:hedge_spider", "", "§c你受到的近战伤害 +5%");
        add("twilightforest:swarm_spider", "", "§c你受到的近战伤害 +5%");
        add("twilightforest:king_spider", "", "§c你受到的近战伤害 +5%");
        add("twilightforest:hostile_wolf", "§a近战攻击 +10%", "");
        add("twilightforest:winter_wolf", "§a近战攻击 +10%", "");
        add("twilightforest:mist_wolf", "§a近战攻击 +10%", "");
        add("twilightforest:fire_beetle", "§a赋予 火焰免疫 效果", "§c你受到的 冰冻 伤害 +12%");
        add("twilightforest:ice_crystal", "§a免疫 冰冻 伤害", "");
        add("twilightforest:stable_ice_core", "§a免疫 冰冻 伤害", "");
        add("twilightforest:unstable_ice_core", "§a免疫 冰冻 伤害", "");
        add("twilightforest:snow_guardian", "§a免疫 冰冻 伤害", "");
        add("twilightforest:maze_slime", "§a免疫 摔落 伤害", "");
        add("twilightforest:pinch_beetle", "", "");
        add("twilightforest:plateau_boss", "§a近战攻击 +15%", "");
        add("twilightforest:redcap", "§a近战攻击 +10%", "");
        add("twilightforest:redcap_sapper", "§a近战攻击 +10%", "");
        add("twilightforest:lower_goblin_knight", "§a近战攻击 +10%", "");
        add("twilightforest:upper_goblin_knight", "§a近战攻击 +10%", "");
        add("twilightforest:blockchain_goblin", "§a近战攻击 +10%", "");
        add("twilightforest:carminite_ghastguard", "§a免疫 爆炸 伤害；§a免疫 弹射物 伤害", "");
        add("twilightforest:carminite_ghastling", "§a免疫 摔落 伤害", "");
        add("twilightforest:carminite_broodling", "§a免疫 摔落 伤害", "");
        add("twilightforest:carminite_golem", "§a免疫 爆炸 伤害；§a免疫 弹射物 伤害", "");
        add("twilightforest:kobold", "§a移动 +10%", "");
        add("twilightforest:slime_beetle", "", "");
        add("twilightforest:skeleton_druid", "§a魔法攻击 +10%", "");
        add("twilightforest:adherent", "§a远程攻击 +10%", "");
        add("twilightforest:bighorn_sheep", "§a免疫 细雪 减速", "");
        add("twilightforest:boar", "§a近战攻击 +5%", "");
        add("twilightforest:deer", "§a移动 +10%", "");
        add("twilightforest:dwarf_rabbit", "", "");
        add("twilightforest:giant_miner", "", "");
        add("twilightforest:lich_minion", "§a魔法攻击 +5%", "");
        add("twilightforest:loyal_zombie", "", "");
        add("twilightforest:mosquito_swarm", "§a移动 +10%", "");
        add("twilightforest:penguin", "§a游泳 +15%", "");
        add("twilightforest:raven", "§a移动 +10%", "");
        add("twilightforest:rising_zombie", "§a近战攻击 +5%", "");
        add("twilightforest:tiny_bird", "§a免疫 摔落 伤害", "");
        add("twilightforest:towerwood_borer", "", "");

        // ── 灾变（重点 + Boss 外） ──
        add("cataclysm:amethyst_crab", "", "");
        add("cataclysm:coral_golem", "", "");
        add("cataclysm:deepling", "§a赋予 水下呼吸 效果", "");
        add("cataclysm:deepling_angler", "§a游泳 +15%", "");
        add("cataclysm:deepling_brute", "§a近战攻击 +10%", "");
        add("cataclysm:deepling_priest", "§a你受到的魔法伤害 -12%", "§c近战伤害 -5%");
        add("cataclysm:deepling_warlock", "§a魔法攻击 +10%", "");
        add("cataclysm:draugr", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("cataclysm:elite_draugr", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("cataclysm:royal_draugr", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("cataclysm:drowned_host", "§a近战攻击 +10%", "");
        add("cataclysm:ender_golem", "§a免疫 弹射物 伤害", "");
        add("cataclysm:endermaptera", "§a你受到的魔法伤害 -12%", "");
        add("cataclysm:hippocamtus", "§a游泳 +20%", "");
        add("cataclysm:ignited_revenant", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("cataclysm:ignited_berserker", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("cataclysm:koboleton", "§a近战攻击 +10%", "");
        add("cataclysm:kobolediator", "§a近战攻击 +10%", "");
        add("cataclysm:lionfish", "§a赋予 水下呼吸 效果；§a游泳 +20%", "§c你受到的近战伤害 +5%");
        add("cataclysm:the_watcher", "§a远程攻击 +15%", "§c近战伤害 -12%");
        add("cataclysm:the_prowler", "§a移动 +10%；§a你受到的远程伤害 -12%", "");
        add("cataclysm:urchinkin", "", "§c你受到的近战伤害 +5%");
        add("cataclysm:symbiocto", "§a赋予 水下呼吸 效果", "");
        add("cataclysm:wadjet", "§a魔法攻击 +10%；§a你受到的魔法伤害 -12%", "");
        add("cataclysm:clawdian", "§a近战攻击 +10%", "");
        add("cataclysm:cindaria", "§a近战攻击 +10%", "");
        add("cataclysm:coralssus", "§a近战攻击 +10%", "");
        add("cataclysm:aptrgangr", "§a近战攻击 +10%", "");
        add("cataclysm:modern_remnant", "", "");
        add("cataclysm:netherite_ministrosity", "", "");
        add("cataclysm:the_baby_leviathan", "§a赋予 水下呼吸 效果；§a游泳 +20%", "");

        // ── 传奇怪物（重点） ──
        add("legendary_monsters:lava_eater", "§a赋予 火焰免疫 效果", "§c你受到的 冰冻 伤害 +12%");
        add("legendary_monsters:overgrown_colossus", "§a赋予 再生1 效果", "");
        add("legendary_monsters:endersent", "§a你受到的魔法伤害 -12%", "§c近战伤害 -5%");
        add("legendary_monsters:the_obliterator", "§a你受到的所有来源伤害 -10%", "§a挥击时 15% 概率向视线方向发射 湮灭激光", "§a湮灭激光：贯穿视线方向；命中造成 等同攻击面板 + 目标最大生命 5% 的伤害");
        add("legendary_monsters:frostbitten_golem", "§a免疫 冰冻 伤害", "§c你受到的 火 伤害 +12%");
        add("legendary_monsters:withered_abomination", "§a免疫 凋零 效果；§a近战攻击 +10%", "§c你受到的治疗量 -10%");
        add("legendary_monsters:skeletosaurus", "§a免疫 箭矢/弹射物 伤害", "");
        add("legendary_monsters:dune_sentinel", "§a移动 +10%", "");
        add("legendary_monsters:shulker_mimic", "§a免疫 弹射物 伤害（反弹弹射物）", "");
        add("legendary_monsters:ancient_guardian", "§a你受到的魔法伤害 -12%", "");
        add("legendary_monsters:annihilation_pursuer", "§a你受到的近战伤害 -12%", "");
        add("legendary_monsters:flame_drifter", "§a赋予 火焰免疫 效果", "§c你受到的 冰冻 伤害 +12%");
        add("legendary_monsters:flameborn_guard", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("legendary_monsters:flameborn_warrior", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("legendary_monsters:ambusher", "§a移动 +10%", "");
        add("legendary_monsters:beheaded_knight", "§a近战攻击 +10%", "");
        add("legendary_monsters:chorusling", "§a免疫 末影珍珠 伤害", "§c你受到的魔法伤害 +12%");
        add("legendary_monsters:knights_armor", "", "");
        add("legendary_monsters:mossy_golem", "", "");
        add("legendary_monsters:resurrected_knight", "§a近战攻击 +10%；§a免疫 凋零 效果", "");
        add("legendary_monsters:skeloraptor", "§a移动 +10%", "");
        add("legendary_monsters:spiky_bug", "", "§c你受到的近战伤害 +5%");
        add("legendary_monsters:fractured", "§a近战攻击 +10%；§a你受到的魔法伤害 -12%", "");
        add("legendary_monsters:fractured_apostle", "§a近战攻击 +10%；§a你受到的魔法伤害 -12%", "");
        add("legendary_monsters:guard", "§a近战攻击 +10%", "");
        add("legendary_monsters:haunted_guard", "§a近战攻击 +10%", "");
        add("legendary_monsters:haunted_knight", "§a近战攻击 +10%", "");
        add("legendary_monsters:hovering_hurricane", "§a移动 +10%", "");
        add("legendary_monsters:stratling", "§a近战攻击 +10%", "");
        add("legendary_monsters:wandering_eye", "§a远程攻击 +10%", "");
        add("legendary_monsters:warped_fungussus", "§a赋予 再生1 效果", "");
        add("legendary_monsters:bomber", "§a免疫 爆炸 伤害", "");

        // ── 永恒星光（重点） ──
        add("eternal_starlight:lunar_monstrosity", "§a你受到的魔法伤害 -12%；§a近战攻击 +10%", "");
        add("eternal_starlight:the_gatekeeper", "§a你受到的所有来源伤害 -12%；§a魔法攻击 +10%", "");
        add("eternal_starlight:starlight_golem", "", "");
        add("eternal_starlight:aethersent_golem", "", "");
        add("eternal_starlight:astral_golem", "", "");
        add("eternal_starlight:grimstone_golem", "", "");
        add("eternal_starlight:ent", "§a赋予 再生1 效果", "");
        add("eternal_starlight:freeze", "§a免疫 冰冻 伤害", "");
        add("eternal_starlight:permafrost", "§a免疫 冰冻 伤害", "");
        add("eternal_starlight:stranghoul", "§a近战攻击 +10%", "");
        add("eternal_starlight:lonestar_skeleton", "§a远程攻击 +10%", "");
        add("eternal_starlight:nightfall_spider", "", "§c你受到的近战伤害 +5%");
        add("eternal_starlight:seeker", "§a移动 +10%", "");
        add("eternal_starlight:tangled", "§a近战攻击 +10%", "");
        add("eternal_starlight:tangled_skull", "§a近战攻击 +10%", "");
        add("eternal_starlight:thirst_walker", "§a近战攻击 +10%", "");
        add("eternal_starlight:ratlin", "§a移动 +10%", "");
        add("eternal_starlight:zombified_ratlin", "§a移动 +10%", "");
        add("eternal_starlight:gleech", "§a移动 +10%", "");
        add("eternal_starlight:boarwarf", "§a近战攻击 +10%", "");
        add("eternal_starlight:aurora_deer", "§a移动 +10%", "");
        add("eternal_starlight:creteor", "§a你受到的魔法伤害 -12%", "");
        add("eternal_starlight:tiny_creteor", "§a你受到的魔法伤害 -12%", "");
        add("eternal_starlight:crystallized_moth", "§a免疫 摔落 伤害", "");
        add("eternal_starlight:luminaris", "§a赋予 水下呼吸 效果；§a游泳 +15%", "");
        add("eternal_starlight:luminofish", "§a赋予 水下呼吸 效果；§a游泳 +15%", "");
        add("eternal_starlight:rookfish", "§a赋予 水下呼吸 效果；§a游泳 +15%", "");
        add("eternal_starlight:shadow_snail", "", "");
        add("eternal_starlight:shimmer_lacewing", "§a移动 +10%", "");
        add("eternal_starlight:solar_creeper", "§a免疫 爆炸 伤害", "");
        add("eternal_starlight:starfire_bird", "§a免疫 摔落 伤害", "");
        add("eternal_starlight:twilight_gaze", "§a远程攻击 +10%", "");
        add("eternal_starlight:yeti", "§a免疫 冰冻 伤害；§a近战攻击 +10%", "");

        // ── 首领崛起（重点） ──
        add("block_factorys_bosses:yeti", "§a免疫 冰冻 伤害；§a赋予 力量1 效果", "");
        add("block_factorys_bosses:underworld_knight", "§a赋予 火焰免疫 效果；§a免疫 凋零 效果", "");
        add("block_factorys_bosses:kraken", "§a赋予 水下呼吸 效果；§a游泳 +30%；§a近战攻击 +10%", "");
        add("block_factorys_bosses:infernal_dragon", "§a赋予 火焰免疫 效果；§a免疫 弹射物 伤害；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("block_factorys_bosses:sandworm", "", "");
        add("block_factorys_bosses:soul_knight_wither_skeleton", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("block_factorys_bosses:soul_skeleton", "§a免疫 凋零 效果；§a近战攻击 +10%", "");
        add("block_factorys_bosses:crossbow_pirate", "§a远程攻击 +10%", "");
        add("block_factorys_bosses:dragon_guard_sword", "§a近战攻击 +10%", "");
        add("block_factorys_bosses:flaming_skeleton_guard_fireball", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("block_factorys_bosses:flaming_skeleton_guard_sword", "§a赋予 火焰免疫 效果；§a近战攻击 +10%", "§c你受到的 冰冻 伤害 +12%");
        add("block_factorys_bosses:ghost_tentacle", "§a赋予 水下呼吸 效果；§a游泳 +15%", "");
        add("block_factorys_bosses:kraken_tentacle", "§a近战攻击 +5%", "");
        add("block_factorys_bosses:pile_of_bones", "", "");
        add("block_factorys_bosses:pirate_captain", "§a近战攻击 +10%；§a远程攻击 +5%", "");
        add("block_factorys_bosses:pirate_rook", "§a近战攻击 +10%", "");

        // ── lensouls 自身 ──
        add("lensouls:twitcher", "§a移动 +10%", "");
    }

    // ========== 药水常驻条目（与描述对应） ==========

    static {
        potion("minecraft:blaze", "minecraft:fire_resistance", 0);
        potion("minecraft:magma_cube", "minecraft:fire_resistance", 0);
        potion("minecraft:zombified_piglin", "minecraft:fire_resistance", 0);
        potion("minecraft:piglin", "minecraft:fire_resistance", 0);
        potion("minecraft:strider", "minecraft:fire_resistance", 0);
        potion("minecraft:cow", "minecraft:regeneration", 0);
        potion("minecraft:mooshroom", "minecraft:regeneration", 0);
        potion("minecraft:spider", "minecraft:jump_boost", 0);
        potion("minecraft:guardian", "minecraft:water_breathing", 0);
        potion("minecraft:dolphin", "minecraft:dolphins_grace", 2);
        potion("minecraft:elder_guardian", "minecraft:dolphins_grace", 2);
        potion("minecraft:axolotl", "minecraft:water_breathing", 0);
        potion("minecraft:turtle", "minecraft:water_breathing", 0);
        potion("minecraft:drowned", "minecraft:water_breathing", 0);
        potion("minecraft:squid", "minecraft:water_breathing", 0);
        potion("minecraft:glow_squid", "minecraft:water_breathing", 0);
        potion("minecraft:cod", "minecraft:water_breathing", 0);
        potion("minecraft:salmon", "minecraft:water_breathing", 0);
        potion("minecraft:tropical_fish", "minecraft:water_breathing", 0);
        potion("minecraft:parrot", "minecraft:jump_boost", 0);
        potion("minecraft:frog", "minecraft:jump_boost", 1);
        potion("twilightforest:hydra", "minecraft:fire_resistance", 0);
        potion("twilightforest:fire_beetle", "minecraft:fire_resistance", 0);
        potion("cataclysm:ignis", "minecraft:fire_resistance", 0);
        potion("cataclysm:ignited_revenant", "minecraft:fire_resistance", 0);
        potion("cataclysm:ignited_berserker", "minecraft:fire_resistance", 0);
        potion("cataclysm:the_leviathan", "minecraft:water_breathing", 0);
        potion("cataclysm:the_leviathan", "minecraft:dolphins_grace", 1);
        potion("cataclysm:deepling", "minecraft:water_breathing", 0);
        potion("cataclysm:lionfish", "minecraft:water_breathing", 0);
        potion("cataclysm:symbiocto", "minecraft:water_breathing", 0);
        potion("cataclysm:the_baby_leviathan", "minecraft:water_breathing", 0);
        potion("legendary_monsters:lava_eater", "minecraft:fire_resistance", 0);
        potion("legendary_monsters:flame_drifter", "minecraft:fire_resistance", 0);
        potion("legendary_monsters:flameborn_guard", "minecraft:fire_resistance", 0);
        potion("legendary_monsters:flameborn_warrior", "minecraft:fire_resistance", 0);
        potion("legendary_monsters:overgrown_colossus", "minecraft:regeneration", 0);
        potion("legendary_monsters:warped_fungussus", "minecraft:regeneration", 0);
        potion("legendary_monsters:posessed_paladin", "minecraft:resistance", 0);
        potion("legendary_monsters:cloud_golem", "minecraft:jump_boost", 1);
        potion("eternal_starlight:ent", "minecraft:regeneration", 0);
        potion("block_factorys_bosses:yeti", "minecraft:strength", 0);
        potion("block_factorys_bosses:underworld_knight", "minecraft:fire_resistance", 0);
        potion("block_factorys_bosses:infernal_dragon", "minecraft:fire_resistance", 0);
        potion("block_factorys_bosses:kraken", "minecraft:water_breathing", 0);
        potion("block_factorys_bosses:ghost_tentacle", "minecraft:water_breathing", 0);
        potion("block_factorys_bosses:flaming_skeleton_guard_fireball", "minecraft:fire_resistance", 0);
        potion("block_factorys_bosses:flaming_skeleton_guard_sword", "minecraft:fire_resistance", 0);
    }

    // ========== 应用逻辑 ==========

    /** 应用该照片的常驻药水 + 元素活性灌注（等级与数据包匹配）。由 PhotoSpecialEffects 每 tick 调用。 */
    public static void applyEffects(LivingEntity player, String entityId) {
        List<PotionEntry> pots = POTIONS.get(entityId);
        if (pots != null) {
            for (PotionEntry p : pots) {
                var inst = player.getEffect(p.effect());
                if (inst == null || inst.getDuration() < 100) {
                    player.addEffect(new MobEffectInstance(p.effect(), 300, p.amplifier(), false, false, true));
                }
            }
        }

        // 元素活性灌注：等级 = attacker_element 数据包等级（药水 amplifier = level - 1）
        var id = ResourceLocation.parse(entityId);
        ElementDamage elem = AttackerElementLoader.getElement(id);
        if (elem != null) {
            int level = AttackerElementLoader.getLevel(id, elem);
            if (level > 0) {
                Holder<MobEffect> effect = switch (elem) {
                    case FIRE -> ModEffects.FIRE_INFUSION;
                    case WATER -> ModEffects.WATER_INFUSION;
                    case EARTH -> ModEffects.EARTH_INFUSION;
                    case ENDER -> ModEffects.ENDER_INFUSION;
                    default -> null;
                };
                if (effect != null) {
                    int amp = Math.max(0, level - 1);
                    var inst = player.getEffect(effect);
                    if (inst == null || inst.getDuration() < 100 || inst.getAmplifier() != amp) {
                        player.addEffect(new MobEffectInstance(effect, 300, amp, false, false, true));
                    }
                }
            }
        }
    }

    // ========== tooltip ==========

    public static String getStolenEntity(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        return tag.contains("lensouls:stolen_entity") ? tag.getString("lensouls:stolen_entity") : null;
    }

    /** 照片主体是否为 Boss（拍摄时打过 lensouls:is_boss 标记） */
    public static boolean isBossPhoto(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.contains("lensouls:is_boss") && tag.getBoolean("lensouls:is_boss");
    }

    public static boolean hasEffect(String entityId) {
        return entityId != null && (DESCRIPTIONS.containsKey(entityId) || POTIONS.containsKey(entityId));
    }

    public static Set<String> getAllEntityIds() {
        Set<String> s = new HashSet<>(DESCRIPTIONS.keySet());
        s.addAll(POTIONS.keySet());
        return s;
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

        PhotoSetRegistry.appendTooltip(event, entityId);

        List<String> lines = DESCRIPTIONS.get(entityId);
        String staticText = lines != null ? String.join("", lines) : "";
        List<Component> attrLines = PhotoSpecialEffects.describeAttributes(entityId, staticText);

        boolean hasDesc = lines != null && lines.stream().anyMatch(l -> l != null && !l.isEmpty());
        if (hasDesc) {
            for (String line : lines) {
                if (line == null || line.isEmpty()) continue;
                event.getToolTip().add(Component.literal(line));
            }
        } else if (attrLines.isEmpty()) {
            event.getToolTip().add(Component.literal("§7无特殊效果").withStyle(ChatFormatting.GRAY));
        }

        // 弱属性照片组：额外照片栏位提示
        int slotBonus = PhotoSpecialEffects.getWeakSlotBonus(entityId);
        if (slotBonus > 0) {
            event.getToolTip().add(Component.literal("§a佩戴时额外获得 " + slotBonus + " 个照片饰品栏位"));
        }

        // 动态元素活性：等级随数据包变化，tooltip 实时跟随
        try {
            var id = ResourceLocation.parse(entityId);
            ElementDamage elem = AttackerElementLoader.getElement(id);
            if (elem != null) {
                int level = AttackerElementLoader.getLevel(id, elem);
                if (level > 0) {
                    String elemName = elementCnName(elem);
                    event.getToolTip().add(Component.literal(
                            "§a佩戴时赋予 " + elemName + "活性" + level + " 效果"));
                }
            }
        } catch (Exception ignored) {
        }

        // 动态属性/元素弱点摘要：使用属性修饰符官方译名，与静态描述去重
        if (!attrLines.isEmpty()) {
            event.getToolTip().add(Component.literal("§6属性效果：").withStyle(ChatFormatting.GOLD));
            for (Component c : attrLines) {
                event.getToolTip().add(c);
            }
        }
    }

    private static String elementCnName(ElementDamage e) {
        return switch (e) {
            case FIRE -> "火";
            case WATER -> "水";
            case EARTH -> "土";
            case ENDER -> "末影";
            default -> e.getSerializedName();
        };
    }
}
