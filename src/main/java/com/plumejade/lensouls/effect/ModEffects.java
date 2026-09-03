package com.plumejade.lensouls.effect;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 模组隐藏效果注册表。
 * <p>
 * 注册四种元素附魔效果：火、水、土、末影。
 * 元素效果通过 assets/lensouls/textures/mob_effect/&lt;name&gt;.png 显示图标，并有独立粒子。
 * <p>
 * 相机滤镜效果（16 个，对应 exposure:expanded 的 16 个滤镜）复用各自滤镜物品的纹理：
 * 将 exposure_expanded 的 item/&lt;base&gt;_filter.png 复制为本模组 mob_effect/filter_&lt;base&gt;.png，
 * 由默认渲染器显示，无自定义客户端代码、无粒子。
 * <p>
 * 如需添加新元素：
 * <ol>
 *   <li>在 {@code ElementDamage} 中添加枚举常量</li>
 *   <li>在本类中用 {@link #register} 注册效果</li>
 *   <li>在 {@code ModItems} 中注册镜魂物品</li>
 *   <li>在 {@code entity_weakness/*.json} 中添加弱点数据</li>
 *   <li>{@link com.plumejade.lensouls.damage.DamageHandler} 自动多态适配，无需修改</li>
 * </ol>
 */
public class ModEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, LenSouls.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> FIRE_INFUSION =
            register("fire_infusion", () -> new ElementInfusionEffect(ElementDamage.FIRE, 0xFF4500));

    public static final DeferredHolder<MobEffect, MobEffect> WATER_INFUSION =
            register("water_infusion", () -> new ElementInfusionEffect(ElementDamage.WATER, 0x1E90FF));

    public static final DeferredHolder<MobEffect, MobEffect> EARTH_INFUSION =
            register("earth_infusion", () -> new ElementInfusionEffect(ElementDamage.EARTH, 0x8B4513));

    public static final DeferredHolder<MobEffect, MobEffect> ENDER_INFUSION =
            register("ender_infusion", () -> new ElementInfusionEffect(ElementDamage.ENDER, 0x9933CC));

    // ========== 元素抑制效果（压制同元素活性，无粒子，HARMFUL） ==========
    public static final DeferredHolder<MobEffect, MobEffect> SUPPRESS_WATER =
            register("suppress_water", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0x1E90FF));
    public static final DeferredHolder<MobEffect, MobEffect> SUPPRESS_EARTH =
            register("suppress_earth", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0x8B4513));
    public static final DeferredHolder<MobEffect, MobEffect> SUPPRESS_FIRE =
            register("suppress_fire", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0xFF4500));
    public static final DeferredHolder<MobEffect, MobEffect> SUPPRESS_ENDER =
            register("suppress_ender", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0x9933CC));

    // ========== 相机滤镜效果（exposure:expanded 16 滤镜 → 16 效果，一一对应） ==========
    /** #1 护甲转伤害·失甲 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BLOBS =
            register("filter_blobs", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #3 血量转攻速·留20血 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_COLOR_CONVOLVE =
            register("filter_color_convolve", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #2 移速比率×伤害 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_SOBEL =
            register("filter_sobel", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #5 每失10%血 -7%移速+25%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_PENCIL =
            register("filter_pencil", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #6 满血+10基础伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_ANTIALIAS =
            register("filter_antialias", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #7 随机4正面buff */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_ART =
            register("filter_art", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #8 +10甲 -50%移速 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BUMPY =
            register("filter_bumpy", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #9 +8韧 -50%移速 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_FLIP =
            register("filter_flip", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #10 水中175%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_NTSC =
            register("filter_ntsc", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #11 火中回血3 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_WOBBLE =
            register("filter_wobble", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #12 每debuff+10%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_SCAN_PINCUSHION =
            register("filter_scan_pincushion", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #14 每debuff+1韧 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_DESATURATE =
            register("filter_desaturate", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #13 每64复制之魂+3伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BITS =
            register("filter_bits", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #16 跳跃高度每格+5伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_DECONVERGE =
            register("filter_deconverge", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #4 空手无甲+70伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BLUR =
            register("filter_blur", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0));
    /** #15 敌人易伤（施加在敌人身上，对应 spider 滤镜） */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_SPIDER =
            register("filter_spider", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0));

    // ========== 浓雾的治愈（每秒回血，可对灾变 Boss 生效） ==========
    public static final DeferredHolder<MobEffect, MobEffect> BOSS_HEAL =
            register("boss_heal", () -> new BossHealEffect(MobEffectCategory.BENEFICIAL, 0x54D054));

    private static DeferredHolder<MobEffect, MobEffect> register(String name, Supplier<MobEffect> effect) {
        return EFFECTS.register(name, effect);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
