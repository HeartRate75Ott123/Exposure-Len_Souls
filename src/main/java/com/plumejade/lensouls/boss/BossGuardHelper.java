package com.plumejade.lensouls.boss;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * BOSS 破刹/定格保护（服务端）。
 * <p>
 * 当玩家对 block_factorys_bosses 的 infernal_dragon / yeti / kraken / sandworm /
 * underworld_knight 触发破刹（韧性打破）或时间定格时，给予玩家抗性提升 V
 * （80% 减伤）与 100% 满抗击退，持续与破刹定身/定格相同的时间。
 */
public final class BossGuardHelper {

    private static final ResourceLocation ID_INFERNAL_DRAGON =
            ResourceLocation.fromNamespaceAndPath("block_factorys_bosses", "infernal_dragon");
    private static final ResourceLocation ID_YETI =
            ResourceLocation.fromNamespaceAndPath("block_factorys_bosses", "yeti");
    private static final ResourceLocation ID_KRAKEN =
            ResourceLocation.fromNamespaceAndPath("block_factorys_bosses", "kraken");
    private static final ResourceLocation ID_SANDWORM =
            ResourceLocation.fromNamespaceAndPath("block_factorys_bosses", "sandworm");
    private static final ResourceLocation ID_UNDERWORLD_KNIGHT =
            ResourceLocation.fromNamespaceAndPath("block_factorys_bosses", "underworld_knight");

    private static final ResourceLocation KB_ID =
            ResourceLocation.fromNamespaceAndPath("lensouls", "boss_guard_kb");
    private static final String TAG_UNTIL = "lensouls_boss_guard_until";

    private BossGuardHelper() {}

    /** 是否是需要给玩家保护的 BOSS。 */
    public static boolean isProtectedBoss(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return ID_INFERNAL_DRAGON.equals(id)
                || ID_YETI.equals(id)
                || ID_KRAKEN.equals(id)
                || ID_SANDWORM.equals(id)
                || ID_UNDERWORLD_KNIGHT.equals(id);
    }

    /** 对玩家施加抗5 + 满抗击退，持续 durationTicks 刻。 */
    public static void apply(ServerPlayer player, int durationTicks) {
        if (durationTicks <= 0) return;
        // 抗性提升 V（amplifier 4 = 5 级，80% 减伤）
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, durationTicks, 4, false, false, true));
        // 满抗性击退（100%）
        AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) {
            kb.removeModifier(KB_ID);
            kb.addTransientModifier(new AttributeModifier(KB_ID, 1.0, AttributeModifier.Operation.ADD_VALUE));
        }
        player.getPersistentData().putLong(TAG_UNTIL, player.serverLevel().getGameTime() + durationTicks);
    }

    /** 服务端每 tick 调用：到期移除抗击退 modifier。 */
    public static void tick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            long until = p.getPersistentData().getLong(TAG_UNTIL);
            if (until <= 0) continue;
            if (p.serverLevel().getGameTime() >= until) {
                AttributeInstance kb = p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
                if (kb != null) kb.removeModifier(KB_ID);
                p.getPersistentData().remove(TAG_UNTIL);
            }
        }
    }
}