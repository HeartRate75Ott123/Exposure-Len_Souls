package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.handler.PhantomRemnantData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 虚影残像：玩家死亡后留在原地的托管容器。
 * <p>
 * 视觉与行为细节复刻 Enigmatic Legacy 的 Extradimensional Vessel
 * （其实现为自定义 {@code PermanentItemEntity extends Entity} + 专用渲染器）：
 * <ul>
 *   <li><b>粒子</b>：客户端每 tick 在实体中心撒 {@link ParticleTypes#PORTAL}（随机速度环绕）——
 *       这是 EL 容器最醒目的一处视效，之前完全漏掉；</li>
 *   <li><b>钉在原地</b>：服务端每 tick 把位置校正回生成锚点（EL 同款），比单纯归零速度更彻底，
 *       水流/活塞/传送都无法挪动它；锚点随实体 NBT 保存，重启后依然钉住；</li>
 *   <li><b>不随维度改变</b>：{@code getDimensionChangingDelay} 取最大值；</li>
 *   <li><b>永不消失</b>：{@code setUnlimitedLifetime()}；</li>
 *   <li><b>完全免疫</b>：免疫一切伤害、免疫火焰、不可攻击、不可推动、不受流体推动；</li>
 *   <li><b>仅所有者可拾取</b>：{@code playerTouch} 自行判定；</li>
 *   <li><b>靠近即自动归还</b>：拾取瞬间按原槽位归还（含 Curios）并销毁容器。</li>
 * </ul>
 */
public class PhantomRemnantEntity extends ItemEntity {

    private static final String TAG_OWNER = "lensouls:remnant_owner";
    private static final String TAG_ANCHOR_X = "lensouls:anchor_x";
    private static final String TAG_ANCHOR_Y = "lensouls:anchor_y";
    private static final String TAG_ANCHOR_Z = "lensouls:anchor_z";

    /** 生成锚点：服务端每 tick 把实体钉回这里 */
    private Vec3 anchor;

    public PhantomRemnantEntity(EntityType<? extends PhantomRemnantEntity> type, Level level) {
        super(type, level);
        lensouls$applyTraits();
    }

    public PhantomRemnantEntity(ServerLevel level, double x, double y, double z, ItemStack stack, UUID owner) {
        // ⚠ 必须显式传入本模组的 EntityType，绝不能用原版
        //   ItemEntity(Level, x, y, z, ItemStack) —— 它内部第一行写死 this(EntityType.ITEM, level)，
        //   会把这个实体的「类型」标成 minecraft:item：
        //     服务端 Java 类是 PhantomRemnantEntity，客户端却按类型造出原版 ItemEntity，
        //     于是走原版 ItemEntityRenderer（1×）、不进自定义渲染器、不 tick → 无缩放无本地粒子。
        //   （实测症状：死亡后只看到一个「发光的普通掉落物」，正是 setGlowingTag 的原版物品。）
        this(ModEntities.PHANTOM_REMNANT.get(), level);
        this.setPos(x, y, z);
        this.setItem(stack);
        this.getPersistentData().putUUID(TAG_OWNER, owner);
        this.anchor = new Vec3(x, y, z);
        lensouls$applyTraits();
    }

    private void lensouls$applyTraits() {
        this.setUnlimitedLifetime();   // 永不自然消失
        this.setNoGravity(true);       // 悬浮
        this.setDeltaMovement(Vec3.ZERO);
        this.setNeverPickUp();         // 原版拾取逻辑禁用，改由 playerTouch 自行判定
        this.setGlowingTag(true);
        this.setInvulnerable(true);
        // EL 同款：固定朝向 + 起浮相位（渲染器用它做 sin 浮动）
        if (this.getYRot() == 0.0F) {
            this.setYRot(this.random.nextFloat() * 360.0F);
        }
    }

    /** 所有者 UUID（仅其本人可拾取） */
    public UUID getOwnerUuid() {
        var pd = this.getPersistentData();
        return pd.hasUUID(TAG_OWNER) ? pd.getUUID(TAG_OWNER) : null;
    }

    // ==================== 完全免疫外力 ====================

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // 完全免疫推动：空实现
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    /** EL 同款：不随维度改变（防止传送门把它带走） */
    @Override
    public int getDimensionChangingDelay() {
        return Short.MAX_VALUE;
    }

    // ==================== tick ====================

    @Override
    public void tick() {
        // EL 同款：内容为空就消失。
        // 只能在服务端判定——客户端实体的 DATA_ITEM 在第 1 tick 可能尚未同步，
        // 若在客户端也 discard，就会出现「服务端有实体、客户端什么都不画也不出粒子」。
        if (!this.level().isClientSide && this.getItem().isEmpty()) {
            this.discard();
            return;
        }

        // 服务端：钉死锚点（EL 同款，比归零速度更彻底）
        if (!this.level().isClientSide && this.anchor != null && !this.position().equals(this.anchor)) {
            this.setPos(this.anchor.x, this.anchor.y, this.anchor.z);
            this.setDeltaMovement(Vec3.ZERO);
        }

        super.tick();

        // 悬浮：抵消一切位移来源
        this.setDeltaMovement(Vec3.ZERO);

        // 粒子：
        //  1) 客户端本地粒子（EL 原做法，逐 tick 一枚）
        //  2) 服务端广播（ServerLevel.sendParticles）——不依赖客户端实体/渲染器路径，
        //     即使客户端那条链有问题，粒子也照常出现，同时用作二者是否生效的判定依据
        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.PORTAL,
                    this.getX(), this.getY() + this.getBbHeight() / 2.0, this.getZ(),
                    (this.random.nextDouble() - 0.5) * 2.0,
                    (this.random.nextDouble() - 0.5) * 2.0,
                    (this.random.nextDouble() - 0.5) * 2.0);
        } else if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    this.getX(), this.getY() + this.getBbHeight() / 2.0, this.getZ(),
                    1, 0.3, 0.3, 0.3, 0.02);
        }
    }

    // ==================== 存档 ====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.anchor != null) {
            tag.putDouble(TAG_ANCHOR_X, this.anchor.x);
            tag.putDouble(TAG_ANCHOR_Y, this.anchor.y);
            tag.putDouble(TAG_ANCHOR_Z, this.anchor.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_ANCHOR_X)) {
            this.anchor = new Vec3(tag.getDouble(TAG_ANCHOR_X),
                    tag.getDouble(TAG_ANCHOR_Y), tag.getDouble(TAG_ANCHOR_Z));
        }
    }

    // ==================== 拾取 ====================

    @Override
    public void playerTouch(Player player) {
        ItemStack stack = this.getItem();
        if (stack.isEmpty()) return;

        UUID owner = getOwnerUuid();
        if (owner != null && !owner.equals(player.getUUID())) return;   // 仅所有者

        if (!(player instanceof ServerPlayer serverPlayer)) return;

        try {
            if (!PhantomRemnantData.hasPayload(stack)) {
                // 空容器（理论不会出现）：直接把物品本体交还
                if (serverPlayer.getInventory().add(stack)) {
                    this.discard();
                }
                return;
            }
            if (PhantomRemnantData.restore(serverPlayer, stack)) {
                this.discard();
            }
        } catch (Exception e) {
            com.plumejade.lensouls.LenSouls.LOGGER.error("[PhantomRemnant] 拾取结算异常", e);
        }
    }
}
