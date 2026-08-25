package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * 焰魔照片「炽焰烙印」自有实现。
 * <p>
 * 绕过灾变 {@code Blazing_Brand} 效果（固定 -20%，不随等级放大），改为直接对承受方
 * 施加稳定的临时护甲/韧性减益修饰符，层数随触发次数叠加（上限 {@link #MAX_LEVEL}），
 * 到期自动清除。近战触发（{@code PhotoSpecialEffects}）与焰魔火球弹幕命中（兼容 Mixin）
 * 两条路径均调用 {@link #applyIgnisArmorBreak}，叠加生效。
 */
public final class IgnisBrandHandler {

    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ignis_brand_armor");
    private static final ResourceLocation TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "ignis_brand_toughness");

    private static final String KEY_LEVEL = "lensouls:ignis_brand_level";
    private static final String KEY_UNTIL = "lensouls:ignis_brand_until";
    private static final int MAX_LEVEL = 4;
    /** 每层减益比例（护甲 / 韧性各 -50% / 层，ADD_MULTIPLIED_TOTAL 乘区） */
    public static final float ARMOR_REDUCE = 0.5f;
    public static final float TOUGHNESS_REDUCE = 0.5f;
    /** 持续时间（游戏刻）：约 10 秒 */
    private static final long DURATION = 200L;

    /** 当前带有烙印的实体 → 到期游戏时刻（强引用，死亡/到期即移除） */
    private static final Map<LivingEntity, Long> BRANDED = new HashMap<>();

    private IgnisBrandHandler() {
    }

    /** 对承受方叠加一层炽焰烙印（减甲减韧），最多叠到 MAX_LEVEL 层。 */
    public static void applyIgnisArmorBreak(LivingEntity target) {
        if (target.level().isClientSide) return;
        var pd = target.getPersistentData();
        int level = Math.min(pd.getInt(KEY_LEVEL) + 1, MAX_LEVEL);
        pd.putInt(KEY_LEVEL, level);
        long until = target.level().getGameTime() + DURATION;
        pd.putLong(KEY_UNTIL, until);

        float armorAmt = ARMOR_REDUCE * level;
        float toughAmt = TOUGHNESS_REDUCE * level;
        applyModifier(target, Attributes.ARMOR, ARMOR_ID, -armorAmt);
        applyModifier(target, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, -toughAmt);

        BRANDED.put(target, until);
    }

    private static void applyModifier(LivingEntity target, Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = target.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clear(LivingEntity target) {
        AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.removeModifier(ARMOR_ID);
        AttributeInstance tough = target.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (tough != null) tough.removeModifier(TOUGHNESS_ID);
        target.getPersistentData().remove(KEY_LEVEL);
        target.getPersistentData().remove(KEY_UNTIL);
    }

    /** 服务端每 20 tick 清理到期/已亡实体的烙印。 */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (var it = BRANDED.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            LivingEntity entity = entry.getKey();
            if (!entity.isAlive() || entity.level().getGameTime() >= entry.getValue()) {
                clear(entity);
                it.remove();
            }
        }
    }
}
