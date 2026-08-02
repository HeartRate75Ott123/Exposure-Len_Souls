package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.Map;

/**
 * 羽·元素觉醒者效果处理器。
 * <p>
 * 佩戴检测：Curios 任意槽位（findFirstCurio 遍历所有槽）。
 * 效果：
 * <ul>
 *   <li>受到伤害 +60%（LivingDamageEvent.Pre 受害者为佩戴者）</li>
 *   <li>造成伤害 +75%（LivingDamageEvent.Pre 伤害来源为佩戴者）</li>
 *   <li>常驻全药水活性 +2 级（每 20 tick 维护 base 记录，效果变化时升级）</li>
 * </ul>
 * 佩戴者击杀 BOSS 不掉落复制之魂、无法使用复制之魂由 CopySoulDropHandler / CraftingMenuMixin 处理。
 */
public class FeatherElementRiseHandler {

    /** 玩家 persistent data 顶层键：药水活性 base 记录（ListTag<{id, amp}>） */
    private static final String TAG_BOOST = "lensouls:feather_boost";

    /** 受击伤害倍率（+60%） */
    public static final float DAMAGE_TAKEN_MULTIPLIER = 1.6f;
    /** 造成伤害倍率（+75%） */
    public static final float DAMAGE_DEALT_MULTIPLIER = 1.75f;
    /** 全药水活性加成等级 */
    public static final int POTION_BOOST_LEVEL = 2;

    /** 佩戴检测：Curios 任意槽位持有羽毛 */
    public static boolean hasFeather(Player player) {
        if (player == null) return false;
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(s -> s.is(ModItems.FEATHER_ELEMENTRISE.get())).isPresent())
                .orElse(false);
    }

    /** 受到伤害 +60% */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && hasFeather(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_TAKEN_MULTIPLIER);
        }
    }

    /** 造成伤害 +75% */
    @SubscribeEvent
    public static void onDealDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && hasFeather(player)) {
            event.setNewDamage(event.getNewDamage() * DAMAGE_DEALT_MULTIPLIER);
        }
    }

    /** 常驻全药水活性 +2 级（每 20 tick 维护） */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        if (!hasFeather(player)) return;
        boostPotions(player);
    }

    /** 临时调试：右键佩戴链路检查 */
    @SubscribeEvent
    public static void onRightClickDebug(PlayerInteractEvent.RightClickItem evt) {
        var stack = evt.getItemStack();
        if (!(stack.getItem() instanceof com.plumejade.lensouls.item.FeatherElementRiseItem)) return;
        var curioOpt = CuriosApi.getCurio(stack);
        com.plumejade.lensouls.LenSouls.LOGGER.info("[Feather] right-click item={} instanceofICurioItem={} cap={}",
                stack, stack.getItem() instanceof top.theillusivec4.curios.api.type.capability.ICurioItem, curioOpt.isPresent());
        CuriosApi.getCuriosInventory(evt.getEntity()).ifPresent(inv -> {
            for (var e : inv.getCurios().entrySet()) {
                var h = e.getValue();
                var dh = h.getStacks();
                for (int i = 0; i < dh.getSlots(); i++) {
                    boolean active = h.getActiveStates().size() > i && h.getActiveStates().get(i);
                    var ctx = new top.theillusivec4.curios.api.SlotContext(e.getKey(), evt.getEntity(), i, false, true);
                    boolean valid = dh.isItemValid(i, stack);
                    boolean fromUse = curioOpt.map(c -> c.canEquipFromUse(ctx)).orElse(false);
                    com.plumejade.lensouls.LenSouls.LOGGER.info("[Feather] slot={} idx={} active={} isItemValid={} canEquipFromUse={}",
                            e.getKey(), i, active, valid, fromUse);
                }
            }
        });
    }

    /**
     * 对所有活跃效果做活性 +2 维护。
     * 效果 id 首次出现或活性被外部改变时重新记录 base 并升级；
     * 效果自然到期/被清除后记录随之清理。
     */
    private static void boostPotions(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        Map<String, Integer> boosted = readBoostRecords(root);

        ListTag newRecords = new ListTag();
        boolean dirty = false;
        for (MobEffectInstance inst : new java.util.ArrayList<>(player.getActiveEffects())) {
            String id = inst.getEffect().value().getDescriptionId();
            int current = inst.getAmplifier();
            Integer recorded = boosted.get(id);

            if (recorded != null && recorded == current) {
                newRecords.add(makeRecord(id, current));
                continue;
            }

            player.addEffect(new MobEffectInstance(inst.getEffect(), inst.getDuration(), current + POTION_BOOST_LEVEL,
                    inst.isAmbient(), inst.isVisible(), inst.showIcon()));
            newRecords.add(makeRecord(id, current + POTION_BOOST_LEVEL));
            dirty = true;
        }

        if (dirty || newRecords.size() != boosted.size()) {
            root.put(TAG_BOOST, newRecords);
        }
    }

    private static Map<String, Integer> readBoostRecords(CompoundTag root) {
        Map<String, Integer> map = new HashMap<>();
        ListTag list = root.getList(TAG_BOOST, Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag c = (CompoundTag) t;
            map.put(c.getString("id"), c.getInt("amp"));
        }
        return map;
    }

    private static CompoundTag makeRecord(String id, int amp) {
        CompoundTag c = new CompoundTag();
        c.putString("id", id);
        c.putInt("amp", amp);
        return c;
    }
}
