package com.plumejade.lensouls.ability.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.AbilityManager;
import com.plumejade.lensouls.ability.AbilityType;
import com.plumejade.lensouls.ability.CameraAbilityStore;
import com.plumejade.lensouls.boss.BossTierLoader;
import com.plumejade.lensouls.boss.BossToughnessAttributes;
import com.plumejade.lensouls.boss.BossToughnessManager;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.item.LensItem;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 要害打击（VITAL_STRIKE）处理器。
 * <p>
 * VITAL_STRIKE 能力激活、取景器已打开的情况下右键相机，不消耗相纸，
 * 对瞄准的 BOSS 实体造成 1 点削韧伤害，并播放快门音效。
 */
public class VitalStrikeHandler {

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");
    private static final ResourceLocation CAMERA_ACTIVE_KEY = ResourceLocation.parse("exposure:camera_active");

    private static final double MAX_RANGE = 24.0;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickCamera(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemStack();
        if (!isCamera(stack)) return;

        // ── 检查当前能力（手持相机） ──
        if (CameraAbilityStore.getSelected(player) != AbilityType.VITAL_STRIKE) return;

        // ── 检查附魔 ──
        if (ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), stack) <= 0) return;

        // ── 检查取景器是否已打开（第一次右键激活取景器，第二次才是快门） ──
        if (!isViewfinderOpen(stack)) return;

        // ── 取消事件 + 不消耗相纸 + 直接削韧 ──
        event.setCanceled(true);

        // ── 寻找瞄准的 BOSS ──
        List<LivingEntity> bosses = findAllBossesInSight(player);
        if (bosses.isEmpty()) return;

        // ── 逐个削韧（视锥内全部 BOSS 均生效） ──
        BossToughnessManager manager = BossToughnessManager.getInstance();
        boolean tierFailedShown = false;
        /** 视锥内是否至少有一只"真的被削到韧性" —— 有就不该再弹失败叉号 */
        boolean anyDealt = false;
        /** 是否有目标是被白霸体挡下的（定身中不算） */
        boolean blockedByHyperArmor = false;

        for (LivingEntity boss : bosses) {
            // ── 镜头等级检测 ──
            int bossTier = BossTierLoader.getTier(boss);
            if (bossTier > 0) {
                int lensTier = getLensTier(stack);
                if (lensTier < bossTier) {
                    if (!tierFailedShown) {
                        com.plumejade.lensouls.network.HudHintPacket.send(player,
                                net.minecraft.network.chat.Component.translatable(
                                        "message.lensouls.lens_tier_insufficient", bossTier));
                        tierFailedShown = true;
                    }
                    continue;
                }
            }

            // ── 削韧 ──
            if (!manager.has(boss)) manager.register(boss);
            // 先取削韧前的状态：命中后无敌窗口会被写进去，事后问不出来"当时是不是白霸体"
            var before = manager.get(boss);
            // 定身中必须先判 broken：`BossToughnessData.tick()` 在 broken 后不再递减无敌计时，
            // 于是 isInvincible() 会残留 true —— 直接拿它判断会把"定身时拍摄"误当成白霸体挡下
            // （用户实测：定身期间按快门也会闪过故障叉号）。
            boolean wasBroken = before != null && before.isBroken();
            boolean wasInvincible = before != null && before.isInvincible() && !wasBroken;

            var result = manager.hitEx(boss, player);
            if (result.data() == null) continue;
            if (result.dealt()) {
                anyDealt = true;
                com.plumejade.lensouls.network.VitalStrikeFxPacket.send(boss);
                // "这次白霸体时长缩短 50%" 只对**真的打进去的**那一下生效。
                // 原来这段在 dealt/没 dealt 之外无条件执行，于是白霸体期间右键虽然削不动，
                // 却会把剩余无敌时长重新写成长度的一半 —— 表现为"miss 反而刷新了白霸体"（已修）。
                int interval = BossToughnessAttributes.getInvincibleTicks(boss);
                int playerInterval = com.plumejade.lensouls.integration.TrophyModifierHandler
                        .applyInvincibleModifier(player, interval);
                result.data().setInvincibleTicks(Math.max(1, playerInterval / 2));
            } else if (wasInvincible) {
                blockedByHyperArmor = true;
            }
        }

        // 失败反馈聚合：视锥内只要有一只成功削到韧性就不弹；没有目标（前面已 return）或
        // 全部是定身/倍率0 这类"无需解释"的情况也不弹。
        // 弹哪一种由服务端一次定死（客户端只看得到自己手上的相机）：
        //   闪光灯槽装着「机械眼」→ 沿用旧的大故障叉号；
        //   否则 / 以及四个静默能力（能力窃取·弱点透镜·空间扭曲·时空回溯）→ 原版虚弱小图标。
        // 四个静默能力仍<b>不弹大叉号</b>（用户定：那四个不是在建韧），但小图标照弹。
        if (!anyDealt && blockedByHyperArmor) {
            boolean mechanicalEye = com.plumejade.lensouls.integration.ExposureHelper.hasMechanicalEye(stack);
            boolean cross = mechanicalEye
                    && !com.plumejade.lensouls.boss.ToughnessFailureGate.silenceFailure(player);
            com.plumejade.lensouls.network.VitalStrikeRejectPacket.send(player, cross);
        }
    }

    // ========== 工具方法 ==========

    private static boolean isCamera(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }

    /** 遍历物品的 DataComponent，查找 exposure:camera_active */
    private static boolean isViewfinderOpen(ItemStack stack) {
        for (var entry : stack.getComponents()) {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.type());
            if (CAMERA_ACTIVE_KEY.equals(id)) {
                return entry.value() instanceof Boolean b && b;
            }
        }
        return false;
    }

    private static List<LivingEntity> findAllBossesInSight(ServerPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(MAX_RANGE);
        List<LivingEntity> result = new ArrayList<>();

        for (Entity entity : player.level().getEntities(player, searchBox)) {
            if (entity == player || !entity.isAlive()) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            // 锥角 + 射程判定，支持子部件（娜迦尾巴/九头蛇头等）追溯到本体
            if (!com.plumejade.lensouls.util.AimTargetUtil.isAimedAt(player, living, MAX_RANGE, 30.0)) continue;

            BossToughnessManager manager = BossToughnessManager.getInstance();
            if (!manager.has(living) && !com.plumejade.lensouls.boss.ToughnessDamageHandler.isBoss(living)) continue;

            result.add(living);
        }
        return result;
    }

    private static int getLensTier(ItemStack camera) {
        var stored = camera.get(io.github.mortuusars.exposure.Exposure.DataComponents.LENS);
        if (stored instanceof StoredItemStack s && s.getForReading().getItem() instanceof LensItem lens) {
            return lens.getTier();
        }
        return 0;
    }
}
