package com.plumejade.lensouls.boss;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.LensItem;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.neoforge.api.event.FrameAddedEvent;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

public class ToughnessPhotoHandler {

    @SubscribeEvent
    public static void onFrameAdded(FrameAddedEvent event) {
        try {
            ItemStack camera = event.getCamera();
            int lensTier = 0;
            StoredItemStack stored = camera.get(Exposure.DataComponents.LENS);
            if (stored != null && stored.getForReading().getItem() instanceof LensItem lens) {
                lensTier = lens.getTier();
            }

            var entities = event.getEntitiesInFrame();
            if (entities == null || entities.isEmpty()) return;

            Entity holder = event.getCameraHolderEntity();
            net.minecraft.server.level.ServerPlayer hitter = holder instanceof net.minecraft.server.level.ServerPlayer p ? p : null;
            BossToughnessManager manager = BossToughnessManager.getInstance();
            boolean playedSound = false;
            // 失败反馈聚合（与要害打击同一口径）：任一目标真的削到韧性 ⇒ 不弹故障叉号；
            // 一只都没削到、但至少有一只被**白霸体**挡下 ⇒ 弹一次。
            boolean anyDealt = false;
            boolean blockedByHyperArmor = false;

            // ① 荒厄·封器 反转：被拍摄的敌对生物受到「韧性伤害 × 玩家当前近战伤害」的伤害
            boolean photoDamage = hitter != null
                    && com.plumejade.lensouls.handler.FeatherHardmanHandler.isCameraUnlocked(hitter);
            float photoDamagePoints = 0f;
            float photoDamageMelee = 0f;
            if (photoDamage) {
                photoDamagePoints = Math.max(0f,
                        com.plumejade.lensouls.integration.TrophyModifierHandler.applyHitsModifier(hitter, 1.0f)
                                + com.plumejade.lensouls.handler.FeatherHardmanHandler.toughnessDamageDelta(hitter));
                photoDamageMelee = (float) hitter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            }

            for (LivingEntity entity : entities) {
                // 幻灵（借体 BOSS 及其召唤物）不参与削韧：玩家对幻灵不造成削韧伤害，也不出韧性条/音效
                if (com.plumejade.lensouls.entity.PhantomDamageHandler.isPhantomEntity(entity)) continue;

                // ① e2 反转的拍照伤害：对所有被拍到的敌对生物生效（与削韧等级无关）
                if (photoDamage && entity != hitter
                        && entity instanceof net.minecraft.world.entity.monster.Enemy
                        && entity.isAlive()) {
                    float dmg = photoDamagePoints * photoDamageMelee;
                    if (dmg > 0f) {
                        ((com.plumejade.lensouls.mixin.EntityInvulnerableTimeAccessor) (Object) entity)
                                .lensouls$setInvulnerableTime(0);
                        entity.hurt(hitter.damageSources().playerAttack(hitter), dmg);
                    }
                }

                boolean hasTier = BossTierLoader.getTier(entity) > 0;
                boolean inManager = manager.has(entity);

                if (hasTier && lensTier < 1) {
                    if (!playedSound) failSound(entity, holder);
                    playedSound = true;
                    continue;
                }

                if (hasTier && !inManager && ToughnessDamageHandler.isBoss(entity)) {
                    manager.register(entity);
                    inManager = true;
                }
                if (!inManager) continue;

                int bossTier = BossTierLoader.getTier(entity);
                boolean hyperArmor = wasHyperArmor(manager, entity);
                if (bossTier < 1 || lensTier >= bossTier) {
                    var r = manager.hitEx(entity, hitter);
                    if (r.dealt()) anyDealt = true;
                    else if (hyperArmor) blockedByHyperArmor = true;
                    continue;
                }
                if (!playedSound) failSound(entity, holder);
                playedSound = true;
            }

            // 白霸体期间"试图造成韧性伤害"都该弹一次提示（拍照这条路径也要），
            // 但同一张照片里只要有一只真的削到了就不弹。
            // 弹哪一种：闪光灯槽装着机械眼 → 旧的大故障叉号；否则（含四个静默能力）→ 原版虚弱小图标。
            if (!anyDealt && blockedByHyperArmor && hitter != null) {
                boolean mechanicalEye =
                        com.plumejade.lensouls.integration.ExposureHelper.hasMechanicalEye(camera);
                boolean cross = mechanicalEye && !ToughnessFailureGate.silenceFailure(hitter);
                com.plumejade.lensouls.network.VitalStrikeRejectPacket.send(hitter, cross);
            }
        } catch (Exception e) {
            LenSouls.LOGGER.error("[ToughnessPhoto] fail", e);
        }
    }

    /**
     * 削韧前是否处于「白霸体」。
     * <p>
     * 必须在 {@code hitEx} <b>之前</b>调用：命中会把新的无敌窗口写进去，事后问不出来；
     * 而破韧（定身）后 {@code tick()} 不再递减无敌计时，{@code isInvincible()} 会残留 true，
     * 所以还要求 {@code !isBroken()} —— 否则定身时拍照也会被当成白霸体挡下（已踩过）。
     */
    private static boolean wasHyperArmor(BossToughnessManager manager, LivingEntity entity) {
        var data = manager.get(entity);
        return data != null && data.isInvincible() && !data.isBroken();
    }

    private static void failSound(LivingEntity entity, Entity holder) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity,
                new ToughnessHitSoundPacket(entity.getId(), true));
        if (holder instanceof net.minecraft.server.level.ServerPlayer player) {
            // 能力窃取 / 弱点透镜 / 空间扭曲 / 时空回溯：照片是"拍内容"，不是尝试削韧，不提示
            // （规则见 ToughnessFailureGate）
            if (ToughnessFailureGate.silenceFailure(player)) return;
            // 走最上层弹窗通道：动作栏那条会被取景框压住、取景器开着时甚至完全不画
            com.plumejade.lensouls.network.HudHintPacket.send(player,
                    net.minecraft.network.chat.Component.translatable("message.lensouls.lens_tier_insufficient",
                            BossTierLoader.getTier(entity)));
        }
    }
}
