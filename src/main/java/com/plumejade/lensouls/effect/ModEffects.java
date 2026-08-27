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
 * 所有效果均无纹理图标、不产生粒子、不在 HUD 显示。
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

    // ========== 相机滤镜效果（exposure:expanded 16 滤镜 → 16 效果 + 敌人易伤） ==========
    /** #1 护甲转伤害·失甲 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BLOBS =
            register("filter_blobs", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_blobs"));
    /** #3 血量转攻速·留20血 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_COLOR_CONVOLVE =
            register("filter_color_convolve", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_color_convolve"));
    /** #2 移速比率×伤害 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_SOBEL =
            register("filter_sobel", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_sobel"));
    /** #5 每失10%血 -7%移速+25%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_PENCIL =
            register("filter_pencil", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_pencil"));
    /** #6 满血+10基础伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_ANTIALIAS =
            register("filter_antialias", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_antialias"));
    /** #7 随机4正面buff */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_ART =
            register("filter_art", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_art"));
    /** #8 +10甲 -50%移速 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BUMPY =
            register("filter_bumpy", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_bumpy"));
    /** #9 +8韧 -50%移速 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_FLIP =
            register("filter_flip", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_flip"));
    /** #10 水中175%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_NTSC =
            register("filter_ntsc", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_ntsc"));
    /** #11 火中回血3 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_WOBBLE =
            register("filter_wobble", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_wobble"));
    /** #12 每debuff+10%伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_SCAN_PINCUSHION =
            register("filter_scan_pincushion", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_scan_pincushion"));
    /** #14 每debuff+1韧 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_DESATURATE =
            register("filter_desaturate", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_desaturate"));
    /** #13 每64复制之魂+3伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BITS =
            register("filter_bits", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_bits"));
    /** #16 跳跃高度每格+5伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_DECONVERGE =
            register("filter_deconverge", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_deconverge"));
    /** #4 空手无甲+70伤 */
    public static final DeferredHolder<MobEffect, MobEffect> FILTER_BLUR =
            register("filter_blur", () -> new FilterEffect(MobEffectCategory.BENEFICIAL, 0, "filter_blur"));
    /** #15 敌人易伤（施加在敌人身上） */
    public static final DeferredHolder<MobEffect, MobEffect> VULNERABILITY =
            register("vulnerability", () -> new FilterEffect(MobEffectCategory.HARMFUL, 0, "vulnerability"));

    private static DeferredHolder<MobEffect, MobEffect> register(String name, Supplier<MobEffect> effect) {
        return EFFECTS.register(name, effect);
    }

    public static void register(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
    }
}
