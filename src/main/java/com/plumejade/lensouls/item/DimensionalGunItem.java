package com.plumejade.lensouls.item;
import net.minecraft.world.item.component.CustomData;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.GunAmmoData;
import com.plumejade.lensouls.component.GunKillData;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.entity.GunBulletEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DimensionalGunItem extends Item {

    private static final String KEY_AMMO = "Ammo";
    private static final String KEY_MAX_AMMO = "MaxAmmo";
    private static final String KEY_KILLS = "Kills";
    private static final String KEY_UNLOCKED = "UnlockedAmmos";
    private static final String KEY_SELECTED = "SelectedAmmo";
    private static final String KEY_FIRE_MODE = "FireMode";
    private static final String KEY_LAST_REGEN = "LastRegenTime";
    private static final String KEY_LAST_FIRE_TICK = "LastFireTick";
    private static final String KEY_CHARGE_START = "DGChargeStart";

    private static final String[] AMMO_NAMES = {"ammo.lensouls.overworld", "ammo.lensouls.hell", "ammo.lensouls.ender"};
    private static final String[] FIRE_MODE_NAMES = {"fire_mode.lensouls.semi", "fire_mode.lensouls.auto"};

    public DimensionalGunItem(Properties properties) {
        super(properties);
    }

    // ======================== Use / Charge / Shoot ========================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.consume(stack);

        // 创造模式可自由切换；生存模式自动修正到已解锁类型
        if (!player.isCreative() && !isAmmoUnlocked(stack, getSelectedAmmo(stack))) {
            for (int i = 0; i < 3; i++) {
                if (isAmmoUnlocked(stack, i)) {
                    setSelectedAmmo(stack, i);
                    break;
                }
            }
        }

        int ammo = getAmmo(stack);
        if (ammo <= 0) {
            player.sendSystemMessage(Component.translatable("message.lensouls.dimensional_gun.out_of_ammo").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.consume(stack);
        }
        // 重置全自动的上次开火 tick，避免跨使用周期计算的 elapsed - lastFire 为负
        setLastFireTick(stack, 0);
        // 记录蓄力起始 tick
        write(stack, KEY_CHARGE_START, player.tickCount);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingTicks) {
        if (!(living instanceof Player player) || level.isClientSide) return;
        if (getFireMode(stack) == 1) { // full-auto
            int elapsed = getUseDuration(stack, living) - remainingTicks;
            int lastFire = getLastFireTick(stack);
            int fireRate = Config.DG_FIRE_RATE.get();
            int curAmmo = getAmmo(stack);
            if (elapsed - lastFire >= fireRate && curAmmo > 0) {
                fireBullet(level, player, stack, 1.0f);
                setLastFireTick(stack, elapsed);
            }
            if (getAmmo(stack) <= 0) {
                player.stopUsingItem();
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeCharged) {
        if (!(living instanceof Player player) || level.isClientSide) return;
        if (getFireMode(stack) == 0) { // semi-auto
            // 用蓄力起始 tick 计算实际已用时长
            int startTick = getChargeStart(stack);
            int elapsed = player.tickCount - startTick;
            if (elapsed < 0 || elapsed > 72000) return;
            if (elapsed < 1) return; // 至少 1 tick 才能射
            if (getAmmo(stack) <= 0) return;
            int chargeTicks = getChargeTicks(stack);
            float charge = Math.min(1.0f, elapsed / (float) chargeTicks);
            fireBullet(level, player, stack, charge);
        }
    }

    private void fireBullet(Level level, Player player, ItemStack stack, float charge) {
        int selected = getSelectedAmmo(stack);
        double damage = getScaledDamage(stack);
        double armorPen = getScaledArmorPen(stack);

        var random = player.getRandom();
        Vec3 look = player.getLookAngle();

        // 蓄力精度：满蓄无偏移，短蓄散布由 DG_ACCURACY_OFFSET 控制（约 0.5~1 格/10 格距离）
        float spread = (float) (double) Config.DG_ACCURACY_OFFSET.get() * (1.0f - charge);
        Vec3 velocity = look.add(
                (random.nextDouble() - 0.5) * spread,
                (random.nextDouble() - 0.5) * spread * 0.5,
                (random.nextDouble() - 0.5) * spread
        ).normalize().scale(3.9);

        GunBulletEntity bullet = new GunBulletEntity(level, player, selected, damage, armorPen);
        bullet.setDeltaMovement(velocity);
        level.addFreshEntity(bullet);

        // 一次性尾迹粒子：沿弹道方向散布（零每 tick 开销）
        if (level instanceof ServerLevel sl) {
            Vec3 origin = player.getEyePosition().subtract(0, 0.1, 0);
            Vec3 dir = velocity.normalize();
            var trailParticle = switch (selected) {
                case 0 -> com.plumejade.lensouls.particle.ModParticleTypes.GRASS_BLADE.get();       // 草飘动
                case 2 -> net.minecraft.core.particles.ParticleTypes.PORTAL;                         // 末影人传送门
                default -> net.minecraft.core.particles.ParticleTypes.FLAME;                         // 火焰
            };
            int trailCount = selected == 0 ? 12 : 6; // 原版粒子减半
            for (int i = 0; i < trailCount; i++) {
                double t = 1.5 + random.nextDouble() * 18.0;
                Vec3 pos = origin.add(dir.scale(t));
                pos = pos.add((random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);
                sl.sendParticles(trailParticle, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            }
        }

        // 射击音效：随机音高 0.1~0.3，随机响度
        float shootPitch = 0.8f + random.nextFloat() * 0.2f;
        float shootVolume = 0.8f + random.nextFloat() * 0.4f;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                com.plumejade.lensouls.sound.ModSounds.GUN_SHOOT.get(),
                SoundSource.PLAYERS, shootVolume, shootPitch);

        setAmmo(stack, getAmmo(stack) - 1);
        updateDataComponents(stack);
    }

    // ======================== Ammo Regen ========================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof Player)) return;
        int ammo = getAmmo(stack);
        int maxAmmo = getScaledMaxAmmo(stack);
        if (ammo >= maxAmmo) return;

        long gameTime = level.getGameTime();
        long lastRegen = getLastRegenTime(stack);
        long regenInterval = getRegenIntervalTicks(stack);
        if (gameTime - lastRegen >= regenInterval) {
            int newAmmo = Math.min(maxAmmo, ammo + 1);
            // 合并一次 write：弹药 + 恢复时间
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag();
            tag.putInt(KEY_AMMO, newAmmo);
            tag.putLong(KEY_LAST_REGEN, gameTime);
            stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            stack.set(ModDataComponents.GUN_AMMO.get(), new GunAmmoData(newAmmo, maxAmmo));
            stack.set(ModDataComponents.GUN_KILLS.get(), new GunKillData(getKills(stack)));
        }
    }

    // ======================== Scaling ========================

    private int getScaledMaxAmmo(ItemStack stack) {
        float progress = getFastSlowProgress(stack);
        int base = Config.DG_BASE_MAX_AMMO.get();
        int max = Config.DG_MAX_AMMO.get();
        return base + (int) ((max - base) * progress);
    }

    private double getScaledDamage(ItemStack stack) {
        float progress = getKillProgress(stack);
        int kills = getKills(stack);
        int target = Config.DG_KILL_TARGET.get();
        double base = Config.DG_BASE_DAMAGE.get();
        double dmg = base + 15.0 * progress;
        if (kills >= target) {
            dmg += 20.0;
        }
        return dmg;
    }

    private double getScaledArmorPen(ItemStack stack) {
        float progress = getKillProgress(stack);
        double base = Config.DG_BASE_ARMOR_PEN.get();
        double max = Config.DG_MAX_ARMOR_PEN.get();
        return base + (max - base) * progress;
    }

    public int getChargeTicks(ItemStack stack) {
        float progress = getFastSlowProgress(stack);
        int base = Config.DG_BASE_CHARGE_TIME.get();
        int min = Config.DG_MIN_CHARGE_TIME.get();
        return (int) (base - (base - min) * progress);
    }

    private long getRegenIntervalTicks(ItemStack stack) {
        float progress = getFastSlowProgress(stack);
        int baseTime = Config.DG_BASE_REGEN_TIME.get();
        int minTime = Config.DG_MIN_REGEN_TIME.get();
        int totalSeconds = (int) (baseTime - (baseTime - minTime) * progress);
        int maxAmmo = getScaledMaxAmmo(stack);
        return (totalSeconds * 20L) / maxAmmo;
    }

    private float getKillProgress(ItemStack stack) {
        int kills = getKills(stack);
        int target = Config.DG_KILL_TARGET.get();
        float t = Math.min(1.0f, (float) kills / target);
        // 三次 Hermite S 曲线：前半段快、后半段慢
        return t < 0.5f ? 4.0f * t * t * t : 1.0f - 4.0f * (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    /** 平方根曲线（弹药上限、蓄力时间、恢复间隔用）：前期快、后期慢 */
    private float getFastSlowProgress(ItemStack stack) {
        int kills = getKills(stack);
        int target = Config.DG_KILL_TARGET.get();
        float t = Math.min(1.0f, (float) kills / target);
        return (float) Math.sqrt(t);
    }

    // ======================== Public API (called from packets/handlers) ========================

    public void cycleAmmoType(ItemStack stack, ServerPlayer player) {
        int current = getSelectedAmmo(stack);
        int unlocked = getUnlockedAmmos(stack);
        for (int i = 1; i <= 3; i++) {
            int next = (current + i) % 3;
            if ((unlocked & (1 << next)) != 0) {
                setSelectedAmmo(stack, next);
                player.sendSystemMessage(Component.translatable("message.lensouls.dimensional_gun.cycled",
                        Component.translatable(AMMO_NAMES[next])).withStyle(ChatFormatting.GREEN));
                // 换弹音效：随机响度
                float vol = 0.7f + player.getRandom().nextFloat() * 0.5f;
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        com.plumejade.lensouls.sound.ModSounds.GUN_BULLET_CHANGE.get(),
                        SoundSource.PLAYERS, vol, 1.0f);
                return;
            }
        }
    }

    public void toggleFireMode(ItemStack stack, ServerPlayer player) {
        int mode = getFireMode(stack) == 0 ? 1 : 0;
        setFireMode(stack, mode);
        player.sendSystemMessage(Component.translatable("message.lensouls.dimensional_gun.toggled",
                Component.translatable(FIRE_MODE_NAMES[mode])).withStyle(ChatFormatting.GREEN));
        // 切换模式音效：随机选一个 modechange，随机响度
        var rng = player.getRandom();
        var modeSound = rng.nextBoolean()
                ? com.plumejade.lensouls.sound.ModSounds.GUN_MODE_CHANGE.get()
                : com.plumejade.lensouls.sound.ModSounds.GUN_MODE_CHANGE.get();
        // Both modechange files are in the same sound event, so the JSON picks randomly
        float mv = 0.7f + rng.nextFloat() * 0.5f;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                modeSound, SoundSource.PLAYERS, mv, 1.0f);
    }

    public boolean unlockAmmoType(ItemStack stack, int type) {
        int unlocked = getUnlockedAmmos(stack);
        if ((unlocked & (1 << type)) != 0) return false;
        unlocked |= (1 << type);
        setUnlockedAmmos(stack, unlocked);
        return true;
    }

    public void addKill(ItemStack stack) {
        setKills(stack, getKills(stack) + 1);
        int newMax = getScaledMaxAmmo(stack);
        setMaxAmmo(stack, newMax);
        if (getAmmo(stack) > newMax) setAmmo(stack, newMax);
        updateDataComponents(stack);
    }

    public static void checkDimensionUnlocks(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        String dim = player.level().dimension().location().toString();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof DimensionalGunItem gun) {
                boolean changed = false;
                if (dim.contains("nether")) changed = gun.unlockAmmoType(stack, 1);
                else if (dim.contains("the_end")) changed = gun.unlockAmmoType(stack, 2);
                if (changed) {
                    String typeKey = dim.contains("nether") ? "hell" : "ender";
                    player.sendSystemMessage(Component.translatable("message.lensouls.ammo_unlocked",
                            Component.translatable("ammo.lensouls." + typeKey)).withStyle(ChatFormatting.LIGHT_PURPLE));
                }
            }
        }
    }

    // ======================== CustomData Helpers ========================

    private int getAmmo(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_AMMO); }
    private void setAmmo(ItemStack stack, int val) { write(stack, KEY_AMMO, val); }
    private int getKills(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_KILLS); }
    public void setKills(ItemStack stack, int val) { write(stack, KEY_KILLS, val); }
    private int getSelectedAmmo(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag();
        if (!tag.contains(KEY_SELECTED)) return 0;
        return tag.getInt(KEY_SELECTED);
    }
    private void setSelectedAmmo(ItemStack stack, int val) { write(stack, KEY_SELECTED, val); }
    private int getUnlockedAmmos(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag();
        if (!tag.contains(KEY_UNLOCKED)) return 1; // overworld default
        return tag.getInt(KEY_UNLOCKED);
    }
    private void setUnlockedAmmos(ItemStack stack, int val) { write(stack, KEY_UNLOCKED, val); }
    private int getFireMode(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_FIRE_MODE); }
    private void setFireMode(ItemStack stack, int val) { write(stack, KEY_FIRE_MODE, val); }
    private int getMaxAmmo(ItemStack stack) {
        int v = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_MAX_AMMO);
        return v > 0 ? v : Config.DG_BASE_MAX_AMMO.get();
    }
    private void setMaxAmmo(ItemStack stack, int val) { write(stack, KEY_MAX_AMMO, val); }
    private long getLastRegenTime(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getLong(KEY_LAST_REGEN); }
    private void setLastRegenTime(ItemStack stack, long val) { writeLong(stack, KEY_LAST_REGEN, val); }
    private int getLastFireTick(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_LAST_FIRE_TICK); }
    private void setLastFireTick(ItemStack stack, int val) { write(stack, KEY_LAST_FIRE_TICK, val); }
    private int getChargeStart(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag().getInt(KEY_CHARGE_START); }

    private void write(ItemStack stack, String key, int val) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag();
        tag.putInt(key, val);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }
    private void writeLong(ItemStack stack, String key, long val) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, empty()).copyTag();
        tag.putLong(key, val);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    private void updateDataComponents(ItemStack stack) {
        stack.set(ModDataComponents.GUN_AMMO.get(), new GunAmmoData(getAmmo(stack), getScaledMaxAmmo(stack)));
        stack.set(ModDataComponents.GUN_KILLS.get(), new GunKillData(getKills(stack)));
    }

    private static net.minecraft.world.item.component.CustomData empty() {
        return net.minecraft.world.item.component.CustomData.EMPTY;
    }

    // ======================== Tooltip ========================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int ammo = getAmmo(stack);
        int maxAmmo = getScaledMaxAmmo(stack);
        int kills = getKills(stack);
        int target = Config.DG_KILL_TARGET.get();
        String ammoName = AMMO_NAMES[getSelectedAmmo(stack)];
        String fireMode = FIRE_MODE_NAMES[getFireMode(stack)];

        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.ammo", ammo, maxAmmo).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.ammo_type", Component.translatable(ammoName)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.fire_mode", Component.translatable(fireMode)).withStyle(ChatFormatting.GRAY));
        float progress = getKillProgress(stack);
        boolean maxed = kills >= target;
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.kills", kills, target, Math.round(progress * 100.0f)).withStyle(ChatFormatting.GRAY));
        if (maxed) {
            tooltip.add(Component.translatable("message.lensouls.dimensional_gun.max_level_unlocked").withStyle(ChatFormatting.GOLD));
        }
        double rawDmg = getScaledDamage(stack);
        double rawPen = getScaledArmorPen(stack);
        double rawCharge = getChargeTicks(stack) / 20.0f;
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.stats",
                Math.round(rawDmg * 10) / 10.0,
                (int) Math.round(rawPen),       // 百分比值（config 80 = 80%）
                Math.round(rawCharge * 10) / 10.0
        ).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.ammo_unlock_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.tooltip.control1").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.tooltip.control2").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("message.lensouls.dimensional_gun.tooltip.control3").withStyle(ChatFormatting.YELLOW));
    }

    // ======================== Ammo Bar ========================

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getAmmo(stack) < getScaledMaxAmmo(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * getAmmo(stack) / getScaledMaxAmmo(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x44AAFF;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    private boolean isAmmoUnlocked(ItemStack stack, int type) {
        return (getUnlockedAmmos(stack) & (1 << type)) != 0;
    }
}
