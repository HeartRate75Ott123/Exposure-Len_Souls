package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import com.plumejade.lensouls.util.WeaknessLensPhoto;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弱点透镜照片的三件事：双持右键装机、耐久消耗与销毁、玩家可见描述。
 * <p>
 * ① <b>双持右键放入/更换</b>：一手拿弱点透镜照片、另一手拿附了摄魂术的物品
 * （两个相机除外——它们右键是取景器），右键即把照片装进那件物品的剑槽；
 * 原来只能按键开 GUI（{@code PhotoGuiMenu}）装，现在两条路都通，且共用
 * {@link WeaknessLensPhoto#writeInstalled} 的写入口径（照片数量固定存 1）。
 * <p>
 * ② <b>耐久 100</b>：照片装在武器上后每次「生效」扣 1 点，归零即从武器上销毁。
 * 生效口径 = 该次伤害里照片确实参与了：对指定生物增伤命中，或它提供的元素活性
 * 命中了目标的弱点（见 {@link #onLivingDamagePre}）。打空（命中率未过、伤害为 0）
 * 与没参与的一刀都不扣。
 * <p>
 * ③ tooltip 跟进：照片本体显示「记录弱点 + 耐久」，装了照片的武器显示照片主体与剩余耐久。
 */
public class WeaknessLensHandler {

    /**
     * 同一次右键会为主手、副手各派发一次 {@code RightClickItem}（前一手 PASS 后继续下一手），
     * 按 tick 去重，保证一次点击只装一张。
     */
    private static final Map<UUID, Integer> LAST_HANDLED_TICK = new ConcurrentHashMap<>();

    // ========== ① 双持右键放入 / 更换 ==========

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel() == null || event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 只认「照片那一手」：另一手是目标物品。这样同一次点击无论先派发哪只手都只会处理一次。
        InteractionHand hand = event.getHand();
        ItemStack photo = player.getItemInHand(hand);
        if (!WeaknessLensPhoto.isWeaknessLensPhoto(photo)) return;

        ItemStack target = hand == InteractionHand.MAIN_HAND
                ? player.getOffhandItem() : player.getMainHandItem();
        if (!WeaknessLensPhoto.isInstallTarget(player, target)) return;

        Integer last = LAST_HANDLED_TICK.get(player.getUUID());
        if (last != null && last == player.tickCount) return;
        LAST_HANDLED_TICK.put(player.getUUID(), player.tickCount);
        if (LAST_HANDLED_TICK.size() > 64) {
            int now = player.tickCount;
            LAST_HANDLED_TICK.entrySet().removeIf(e -> e.getValue() < now - 20);
        }

        event.setCanceled(true);
        install(player, hand, photo, target);
    }

    /**
     * 装机目标判定已收敛到 {@link WeaknessLensPhoto#isInstallTarget}——客户端
     * {@code PhotographInstallMixin} 用同一判定决定是否吞掉照片查看界面，两边不能各写一套。
     */

    private static void install(ServerPlayer player, InteractionHand hand, ItemStack photo, ItemStack target) {
        RegistryAccess access = player.registryAccess();

        // 先把被替换下来的照片取出来（换装：旧照片退回背包，绝不凭空吞掉）
        ItemStack replaced = WeaknessLensPhoto.getInstalledPhoto(target, access);
        WeaknessLensPhoto.writeInstalled(target, photo, access);

        // 消耗手里这一张（堆叠时只拿 1 张）
        player.setItemInHand(hand, photo.copyWithCount(photo.getCount() - 1));

        if (!replaced.isEmpty() && !player.getInventory().add(replaced)) {
            player.drop(replaced, false);
        }

        player.displayClientMessage(Component.translatable(replaced.isEmpty()
                ? "message.lensouls.weakness_lens.installed"
                : "message.lensouls.weakness_lens.swapped"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ========== ② 耐久：生效一次 -1，归零销毁 ==========

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (event.getOriginalDamage() <= 0f) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (target == player) return;

        ItemStack weapon = player.getMainHandItem();
        // inspectActive：武器祛魔后照片不再生效，也就不该继续扣耐久
        WeaknessLensPhoto.Installed installed = WeaknessLensPhoto.inspectActive(weapon, player.registryAccess());
        if (!installed.present()) return;

        ResourceLocation targetId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        if (targetId == null) return;

        // 该照片在这一刀里参与了没有：①对照片主体增伤；②它给的元素活性命中了目标弱点
        boolean entityBonus = installed.entityId() != null && installed.entityId().equals(targetId.toString());
        boolean elementBonus = installed.hasElement()
                && DataPackLoader.getWeakness(targetId, installed.element()) > 0f;
        if (!entityBonus && !elementBonus) return;

        RegistryAccess access = player.registryAccess();
        ItemStack photo = WeaknessLensPhoto.getInstalledPhoto(weapon, access);
        if (photo.isEmpty() || !WeaknessLensPhoto.isWeaknessLensPhoto(photo)) return;

        int left = WeaknessLensPhoto.getDurability(photo) - 1;
        if (left <= 0) {
            WeaknessLensPhoto.clearInstalled(weapon);
            player.displayClientMessage(
                    Component.translatable("message.lensouls.weakness_lens.broken"), true);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.9f, 1.1f);
        } else {
            WeaknessLensPhoto.setDurability(photo, left);
            WeaknessLensPhoto.updateInstalledPhoto(weapon, photo, access);
        }
    }

    // ========== ③ tooltip ==========

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        Player player = event.getEntity();
        RegistryAccess access = player != null ? player.registryAccess() : null;

        // 照片本体：记录弱点 + 剩余耐久 + 装机方式
        if (WeaknessLensPhoto.isWeaknessLensPhoto(stack)) {
            ElementDamage element = WeaknessLensPhoto.getRecordedElement(stack, access);
            if (element != null) {
                event.getToolTip().add(Component.translatable("item.lensouls.weakness_lens.element",
                        elementName(element)));
            }
            event.getToolTip().add(durabilityLine("item.lensouls.weakness_lens.durability",
                    WeaknessLensPhoto.getDurability(stack)));
            event.getToolTip().add(Component.translatable("item.lensouls.weakness_lens.hint")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // 装了照片的武器：主体 + 记录元素 + 剩余耐久
        WeaknessLensPhoto.Installed installed = WeaknessLensPhoto.inspect(stack);
        if (!installed.present()) return;
        ItemStack photo = WeaknessLensPhoto.getInstalledPhoto(stack, access);
        if (photo.isEmpty()) return;

        if (installed.entityId() != null) {
            event.getToolTip().add(Component.translatable("item.lensouls.weakness_lens.installed",
                    Component.translatable(PhotographEffectRegistry.entityIdToTranslationKey(installed.entityId()))));
        }
        // 活性只在武器仍附摄魂术时才声明（祛魔后照片留着，但不再生效）
        WeaknessLensPhoto.Installed active = WeaknessLensPhoto.inspectActive(stack, access);
        if (active.hasElement()) {
            event.getToolTip().add(Component.translatable("item.lensouls.weakness_lens.weapon_element",
                    elementName(active.element()), String.valueOf(WeaknessLensPhoto.ACTIVITY_LEVEL)));
        }
        event.getToolTip().add(durabilityLine("item.lensouls.weakness_lens.installed_durability",
                WeaknessLensPhoto.getDurability(photo)));
    }

    private static Component durabilityLine(String key, int durability) {
        return Component.translatable(key,
                String.valueOf(durability), String.valueOf(WeaknessLensPhoto.MAX_DURABILITY));
    }

    /** 元素短名（火/水/土/末影），与元素活性 tooltip 同一套语言键 */
    private static Component elementName(ElementDamage element) {
        return Component.translatable("element.lensouls." + element.getSerializedName() + ".short");
    }
}
