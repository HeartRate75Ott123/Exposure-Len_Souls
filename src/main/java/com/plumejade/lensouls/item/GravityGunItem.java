package com.plumejade.lensouls.item;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.entity.GravityBulletEntity;
import com.plumejade.lensouls.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 引力枪 — 右键直射引力弹，命中后持续牵引。
 *
 * 发射模式：右键 → 发射引力弹（0.2s 原版冷却）
 * 牵引模式：右键 → 释放目标，回到发射模式
 */
public class GravityGunItem extends Item {

    private static final String KEY_ITEM_ID = "GravityGunItemId";
    private static final String KEY_ACTIVE_BULLET = "ActiveBulletId";
    private static final int COOLDOWN_TICKS = 4; // 默认 0.2s，可被 Config.ggCooldown 覆盖

    public GravityGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.consume(stack);

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        // ── 牵引模式：右键取消牵引 ──
        if (tag.contains(KEY_ACTIVE_BULLET) && tag.getInt(KEY_ACTIVE_BULLET) != 0) {
            int bulletId = tag.getInt(KEY_ACTIVE_BULLET);
            Entity bullet = level.getEntity(bulletId);
            if (bullet instanceof GravityBulletEntity) {
                bullet.discard();
            }
            tag.remove(KEY_ACTIVE_BULLET);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            return InteractionResultHolder.consume(stack);
        }

        // ── 发射模式：直射引力弹 ──
        fireBullet(level, player, stack);
        player.getCooldowns().addCooldown(this, Config.GG_COOLDOWN.get());
        return InteractionResultHolder.consume(stack);
    }

    private void fireBullet(Level level, Player player, ItemStack stack) {
        Vec3 velocity = player.getLookAngle().scale(2.0);
        String gunUuid = getOrCreateItemId(stack);
        GravityBulletEntity bullet = new GravityBulletEntity(level, player, gunUuid);
        bullet.setDeltaMovement(velocity);
        level.addFreshEntity(bullet);

        // 白色尾迹（一次性生成）
        if (level instanceof ServerLevel sl) {
            Vec3 origin = player.getEyePosition().subtract(0, 0.1, 0);
            Vec3 dir = velocity.normalize();
            for (int i = 0; i < 4; i++) {
                double t = 1.0 + player.getRandom().nextDouble() * 6.0;
                Vec3 pos = origin.add(dir.scale(t))
                        .add((player.getRandom().nextDouble() - 0.5) * 0.4,
                                (player.getRandom().nextDouble() - 0.5) * 0.4,
                                (player.getRandom().nextDouble() - 0.5) * 0.4);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        }

        // 音效（音量和音调大范围浮动）
        float vol = 0.6f + player.getRandom().nextFloat() * 0.8f;
        float pit = 0.6f + player.getRandom().nextFloat() * 0.8f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.GRAVITY_SHOOT.get(), SoundSource.PLAYERS, vol, pit);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) { return 1; }

    // ======================== UUID ========================

    private String getOrCreateItemId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(KEY_ITEM_ID)) return tag.getString(KEY_ITEM_ID);
        String id = UUID.randomUUID().toString();
        tag.putString(KEY_ITEM_ID, id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return id;
    }

    // ======================== Tooltip ========================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("message.lensouls.gravity_gun.tooltip.control1").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("message.lensouls.gravity_gun.tooltip.control2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("message.lensouls.gravity_gun.tooltip.control3").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("message.lensouls.gravity_gun.tooltip.cooldown").withStyle(ChatFormatting.GRAY));
    }
}
