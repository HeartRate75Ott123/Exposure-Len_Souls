package com.plumejade.lensouls.item;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.component.SoulCooldownData;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.effect.ElementInfusionEffect;
import com.plumejade.lensouls.effect.ModEffects;
import com.plumejade.lensouls.entity.BossPhantomManager;
import com.plumejade.lensouls.entity.BossPhantomType;
import com.plumejade.lensouls.timer.TimerService;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 镜魂物品——可重复使用的元素附魔道具。
 * <p>
 * 右键激活：检测冷却 → 应用对应元素隐藏效果 → 启动冷却。
 * 冷却期间右键提示剩余时间。
 */
public class LensoulItem extends Item {

    private final ElementDamage element;
    private final float damageMultiplier;    // 物品自带倍率（BOSS镜魂 > 1.0）
    private final boolean applySlowness;     // 云筑魔像专属：攻击附带减速
    private final int cooldownSeconds;       // 冷却秒数（0 表示用配置默认值）

    public LensoulItem(ElementDamage element, Properties properties) {
        this(element, 1.0f, false, 0, properties);
    }

    /** BOSS 镜魂专用构造器 */
    public LensoulItem(ElementDamage element, float damageMultiplier, boolean applySlowness, Properties properties) {
        this(element, damageMultiplier, applySlowness, 0, properties);
    }

    /** BOSS 镜魂专用构造器（含自定义冷却时长） */
    public LensoulItem(ElementDamage element, float damageMultiplier, boolean applySlowness, int cooldownSeconds, Properties properties) {
        super(properties);
        this.element = element;
        this.damageMultiplier = damageMultiplier;
        this.applySlowness = applySlowness;
        this.cooldownSeconds = cooldownSeconds;
    }

    /** 获取冷却刻数：BOSS 走 bossCooldown 配置，基础走 defaultCooldown 配置 */
    public int getCooldownTicks() {
        return (cooldownSeconds > 0 ? Config.BOSS_COOLDOWN.get() : Config.DEFAULT_COOLDOWN.get()) * 20;
    }

    public ElementDamage getElement() {
        return element;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public boolean shouldApplySlowness() {
        return applySlowness;
    }

    /**
     * 根据物品倍率返回对应 amplifier 等级。
     * 利用原版效果机制：高 amplifier 不会被低 amplifier 覆盖。
     * 升级后的镜魂从 CustomData 读取等级。
     */
    public int getAmplifier(ItemStack stack) {
        return com.plumejade.lensouls.handler.AnvilUpgradeHandler.getAmplifier(stack);
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        UUID playerId = player.getUUID();
        String soulId = element.getSerializedName();
        String itemUuid = getOrCreateItemId(stack);
        String cooldownId = "soul_item_" + itemUuid;
        TimerService timer = TimerService.getInstance();


        // ---- 冷却检测（TimerService 内存 + CustomData 持久化双源） ----
        boolean onCooldown = timer.isActive(playerId, cooldownId);
        if (!onCooldown) {
            // TimerService 无记录时，查物品 CustomData（跨重启持久化）
            CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
            if (cd != null) {
                CompoundTag cdTag = cd.copyTag();
                if (cdTag.contains("SoulCooldownEnd")) {
                    long end = cdTag.getLong("SoulCooldownEnd");
                    long remaining = end - level.getGameTime();
                    if (remaining > 0) {
                        timer.start(playerId, cooldownId, remaining);
                        onCooldown = true;
                    }
                }
            }
        }
        if (onCooldown) {
            long remainingSec = timer.getRemainingTicks(playerId, cooldownId) / 20;
            player.displayClientMessage(
                    Component.translatable("message.lensouls.soul_cooldown", remainingSec), true);
            return InteractionResultHolder.consume(stack);
        }

        // ---- 启动冷却（独立于效果，低级启用后也进入冷却） ----
        // BOSS 镜魂：已有活跃幻灵时直接跳过，不进冷却
        BossPhantomType checkType = BossPhantomType.fromSoulItem(damageMultiplier, applySlowness, element);
        if (checkType != null && checkType.isModLoaded() && player instanceof ServerPlayer sp
                && BossPhantomManager.getInstance().hasActivePhantom(sp.getUUID())) {
            return InteractionResultHolder.pass(stack);
        }
        int cooldownTicks = getCooldownTicks();
        timer.start(playerId, cooldownId, cooldownTicks);
        long endTime = level.getGameTime() + cooldownTicks;
        // 写入 SoulCooldownData 组件（tooltip 显示用，不依赖持久化）
        stack.set(ModDataComponents.SOUL_COOLDOWN.get(),
                new SoulCooldownData(endTime, cooldownTicks));
        // 写入 CustomData（vanilla 组件，跨游戏重启持久化）
        CompoundTag cdTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        cdTag.putLong("SoulCooldownEnd", endTime);
        cdTag.putInt("SoulCooldownDur", cooldownTicks);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(cdTag));

        // ---- BOSS 镜魂：走虚影幻灵序列（3 秒后自动施加效果） ----
        BossPhantomType phantomType = BossPhantomType.fromSoulItem(damageMultiplier, applySlowness, element);
        if (phantomType != null && phantomType.isModLoaded() && player instanceof ServerPlayer serverPlayer) {
            String descId = stack.getDescriptionId();
            BossPhantomManager.getInstance().startPhantom(serverPlayer, phantomType, descId);

            // 反馈：虚影已降临
            Component soulDisplay = Component.translatable(descId);
            player.sendSystemMessage(
                    Component.translatable("message.lensouls.phantom_start", soulDisplay));
        } else {
            // ---- 基础镜魂 / BOSS 模组未加载：直接施加效果 ----
            applyElementEffect(player, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    // ========== 冷却视觉条（灰色耐久条，仅客户端渲染） ==========

    @Override
    public boolean isBarVisible(ItemStack stack) {
        SoulCooldownData cool = stack.get(ModDataComponents.SOUL_COOLDOWN.get());
        if (cool == null) return false;
        // 冷却结束后隐藏耐久条（避免显示满格黑条）
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            return mc.level != null && cool.remainingTicks(mc.level.getGameTime()) > 0;
        } catch (NoClassDefFoundError ignored) {
            return true; // 服务端默认显示
        }
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        SoulCooldownData cool = stack.get(ModDataComponents.SOUL_COOLDOWN.get());
        if (cool == null) return 0;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return 13;
            long remaining = cool.remainingTicks(mc.level.getGameTime());
            if (remaining > 0) {
                return Math.round(13.0f * (cool.duration() - remaining) / cool.duration());
            }
        } catch (NoClassDefFoundError ignored) {}
        return 0;
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x44CC44; // 绿色
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
            @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltipComponents, flag);

        // ---- 使用提示（始终显示） ----
        tooltipComponents.add(Component.translatable("item.lensouls.soul.use_hint"));

        // ---- BOSS 镜魂额外特效提示 ----
        if (this.damageMultiplier > 1.0f || this.applySlowness) {
            tooltipComponents.add(Component.translatable("item.lensouls.soul.boss_hint")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }

        // ---- 冷却提示（有冷却时显示） ----
        SoulCooldownData cool = stack.get(ModDataComponents.SOUL_COOLDOWN.get());
        if (cool != null) {
            Level level = context.level();
            if (level != null) {
                long remaining = cool.remainingTicks(level.getGameTime());
                if (remaining > 0) {
                    int seconds = (int) ((remaining + 19) / 20);
                    tooltipComponents.add(
                            Component.translatable("message.lensouls.soul_cooldown", seconds)
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
                }
            }
        }
    }

    /** 获取或生成该物品的唯一标识 UUID（存于 CustomData），用于独立冷却判断 */
    public static String getOrCreateItemId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String id;
        if (tag.contains("SoulItemId")) {
            id = tag.getString("SoulItemId");
        } else {
            id = UUID.randomUUID().toString();
            tag.putString("SoulItemId", id);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return id;
    }

    /** 读取物品 UUID（不存在返回 null，不写入） */
    public static String getItemId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains("SoulItemId") ? tag.getString("SoulItemId") : null;
    }

    public Holder<MobEffect> getEffectHolder() {
        return switch (element) {
            case FIRE -> ModEffects.FIRE_INFUSION;
            case WATER -> ModEffects.WATER_INFUSION;
            case EARTH -> ModEffects.EARTH_INFUSION;
            case ENDER -> ModEffects.ENDER_INFUSION;
            case PROJECTILE -> ModEffects.FIRE_INFUSION; // 不应发生，占位编译
        };
    }

    /**
     * 直接施加元素效果（基础镜魂 / BOSS 模组未加载时的降级路径）。
     */
    public void applyElementEffect(Player player, ItemStack stack) {
        int amplifier = getAmplifier(stack);
        Holder<MobEffect> effectHolder = getEffectHolder();
        int durationTicks = Config.DEFAULT_DURATION.get() * 20;
        player.addEffect(new MobEffectInstance(
                effectHolder, durationTicks, amplifier, false, false, false
        ));

        // 始终设置自定义名称（即使效果未变更），确保后续覆盖正确
        String descId = this.damageMultiplier > 1.0f || this.applySlowness ? stack.getDescriptionId() : null;
        ElementInfusionEffect.setPlayerData(player, this.element, this.applySlowness, descId);

        Component soulDisplay = this.damageMultiplier > 1.0f || this.applySlowness
                ? Component.translatable(stack.getDescriptionId())
                : Component.translatable("element.lensouls." + element.getSerializedName());
        player.sendSystemMessage(
                Component.translatable("message.lensouls.soul_activated", soulDisplay));
    }
}
