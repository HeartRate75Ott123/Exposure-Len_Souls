package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

/**
 * 法师胸针效果：
 * <ul>
 *   <li>佩戴者造成的一切伤害以魔法伤害源重打（真改源，护甲/附魔按魔法重算）；</li>
 *   <li>佩戴者普通伤害 -50%（照片弹幕豁免）；</li>
 *   <li>照片弹幕命中时清无敌帧并追加一次等效该次弹幕伤害 ×200% 的魔法伤害（净约 ×3）。</li>
 * </ul>
 * 与灵魂口哨互斥（由物品 canEquip 保证）。
 */
public class BroochEffectHandler {

    /** 魔法化/弹幕追加的同步护栏：当前正在处理的胸针玩家（服务端单线程，hurt 同步嵌套期间置位） */
    private static UUID guardCaster = null;

    private static boolean isMagic(DamageSource src) {
        return src.is(DamageTypes.MAGIC) || src.is(DamageTypes.INDIRECT_MAGIC)
                || src.is(Tags.DamageTypes.IS_MAGIC);
    }

    /** 该玩家是否佩戴法师胸针 */
    public static boolean hasBrooch(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.MAGE_BROOCH.get())).isPresent())
                .orElse(false);
    }

    // ========== 1. 魔法化（真改源）：incoming → cancel + 以魔法源重打 ==========

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getAmount() <= 0f) return;

        Entity caster = event.getSource().getEntity();
        if (!(caster instanceof ServerPlayer player)) return;
        if (event.getEntity() == caster) return;             // 自伤/反伤不转化
        if (!hasBrooch(player)) return;
        if (isMagic(event.getSource())) return;              // 已是魔法源（含我们重打的与弹幕追加的）→ 不再转化
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        event.setCanceled(true);

        DamageSource magic = magicSource(target.level(), event.getSource().getDirectEntity(), player);
        target.hurt(magic, amount);
    }

    // ========== 2. 普通伤害 -50%（照片弹幕豁免）==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide) return;

        Entity caster = event.getSource().getEntity();
        if (!(caster instanceof ServerPlayer player)) return;
        if (event.getEntity() == caster) return;
        if (guardCaster != null && guardCaster.equals(player.getUUID())) return; // 弹幕追加伤害不再被 -50%
        if (!hasBrooch(player)) return;

        Entity direct = event.getSource().getDirectEntity();
        boolean isPhotoProj = direct != null
                && direct.getPersistentData().getBoolean("lensouls:photo_proj");
        if (!isPhotoProj) {
            float cur = event.getNewDamage();
            event.setNewDamage(cur * 0.5f);
        }
    }

    // ========== 3. 弹幕追加 +200% 魔法伤害（清无敌帧后追加，净 ×3）==========

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide) return;

        Entity caster = event.getSource().getEntity();
        if (!(caster instanceof ServerPlayer player)) return;
        if (event.getEntity() == caster) return;
        if (!hasBrooch(player)) return;

        Entity direct = event.getSource().getDirectEntity();
        if (direct == null) return;
        if (!direct.getPersistentData().getBoolean("lensouls:photo_proj")) return;

        float finalDamage = event.getNewDamage();
        if (finalDamage <= 0f) return;

        LivingEntity target = event.getEntity();
        float extra = finalDamage * 2f;   // 等效 200%

        guardCaster = player.getUUID();
        try {
            target.invulnerableTime = 0;   // 清无敌帧，保证追加足额
            target.hurt(magicSource(target.level(), player, player), extra);
        } finally {
            guardCaster = null;
        }
    }

    /** 以原版 magic 类型构造伤害源，保留原直接实体/造成者（真改源关键） */
    private static DamageSource magicSource(Level level, Entity direct, Entity causing) {
        Holder.Reference<DamageType> magic = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE, DamageTypes.MAGIC.location()));
        return new DamageSource(magic, direct, causing);
    }
}
