package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class GunBulletEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Byte> DATA_BULLET_TYPE =
            SynchedEntityData.defineId(GunBulletEntity.class, EntityDataSerializers.BYTE);

    private static final int COLLISION_SEGMENTS = 3; // 多段碰撞插值段数

    private double damage;
    private double armorPen;

    public GunBulletEntity(EntityType<? extends GunBulletEntity> type, Level level) {
        super(type, level);
    }

    public GunBulletEntity(Level level, LivingEntity shooter, int bulletType, double damage, double armorPen) {
        super(ModEntities.GUN_BULLET.get(), level);
        setOwner(shooter);
        this.damage = damage;
        this.armorPen = armorPen;
        entityData.set(DATA_BULLET_TYPE, (byte) bulletType);
        // 同步弹射物物品栈到客户端，使 ThrownItemRenderer 渲染正确的弹药贴图
        setItem(getDefaultItem().getDefaultInstance());
        setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BULLET_TYPE, (byte) 0);
    }

    @Override
    protected Item getDefaultItem() {
        if (entityData == null) return ModItems.OVERWORLD_BULLET.get();
        return switch (entityData.get(DATA_BULLET_TYPE)) {
            case 1 -> ModItems.HELL_BULLET.get();
            case 2 -> ModItems.ENDER_BULLET.get();
            default -> ModItems.OVERWORLD_BULLET.get();
        };
    }

    @Override
    public void tick() {
        Vec3 oldPos = position();
        Vec3 vel = getDeltaMovement();

        // [DEBUG] 首个 tick 日志
        if (tickCount == 0) {
        }

        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);

        if (!level().isClientSide) {
            // 将 AABB 偏移回 oldPos 作为基准，避免 getBoundingBox() 在 setPos 后已指向 newPos
            AABB baseBox = getBoundingBox().move(oldPos.subtract(position()));
            Vec3 step = vel.scale(1.0 / COLLISION_SEGMENTS);

            for (int seg = 0; seg < COLLISION_SEGMENTS; seg++) {
                Vec3 segStart = oldPos.add(step.scale(seg));
                Vec3 segEnd = segStart.add(step);
                AABB searchBox = baseBox.move(step.scale(seg)).expandTowards(step).inflate(1.0);

                int candidates = 0;
                for (Entity target : level().getEntities(this, searchBox, this::canHitEntity)) {
                    candidates++;
                    AABB targetBox = target.getBoundingBox();
                    double minDim = Math.min(Math.min(targetBox.getXsize(), targetBox.getYsize()), targetBox.getZsize());
                    if (minDim < 0.6) targetBox = targetBox.inflate(0.3);

                    var clipResult = targetBox.clip(segStart, segEnd);
                    if (clipResult.isPresent()) {
                        onHit(new EntityHitResult(target, clipResult.get()));
                        return;
                    }
                }
                if (candidates > 0 && seg == 0) {
                }
            }
            if (tickCount % 20 == 0) {
            }
            if (tickCount > 200) discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target.equals(getOwner())) { discard(); return; }
        if (!(target instanceof LivingEntity living)) { discard(); return; }
        if (level().isClientSide) { discard(); return; }

        float finalDamage = (float) damage;
        Entity owner = getOwner();
        byte type = entityData.get(DATA_BULLET_TYPE);

        // 使用自定义 gun_bullet 伤害类型（带 cataclysm:bypasses_hurt_time 标签，绕过 DPS 桶）
        DamageSource gunSource = gunBulletDamage(owner instanceof LivingEntity lo ? lo : null);

        // 地狱弹：先点燃目标
        if (type == 1) {
            living.setRemainingFireTicks(Config.DG_HELL_FIRE_DURATION.get() * 20);
        }
        living.invulnerableTime = 0;
        living.hurt(gunSource, finalDamage);

        switch (type) {
            case 0 -> { if (owner instanceof LivingEntity lo) lo.heal((float) (double) Config.DG_OVERWORLD_HEAL.get()); }
            case 2 -> {
                if (owner != null) {
                    Vec3 pull = owner.position().subtract(living.position()).normalize()
                            .scale(Config.DG_ENDER_PULL_FORCE.get());
                    living.setDeltaMovement(living.getDeltaMovement().add(pull));
                    living.hurtMarked = true;
                }
            }
        }

        // 命中粒子效果（按弹药类型：主世界→亮绿，地狱→橙，末地→紫）
        if (level() instanceof ServerLevel sl) {
            var pType = switch (type) {
                case 0 -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK.get();      // 亮绿
                case 2 -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_PURPLE.get(); // 紫
                default -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_ORANGE.get(); // 橙
            };
            sl.sendParticles(pType, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(),
                    9, 0.5, 0.3, 0.5, 0.15);
        }

        discard();
    }

    @Override public boolean isNoGravity() { return true; }
    @Override protected boolean canHitEntity(Entity target) { return target instanceof LivingEntity && !target.equals(getOwner()); }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); damage = tag.getDouble("Damage"); armorPen = tag.getDouble("ArmorPen"); }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putDouble("Damage", damage); tag.putDouble("ArmorPen", armorPen); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 4096; }

    /** 创建 lensouls:gun_bullet 伤害源（带 cataclysm:bypasses_hurt_time 标签，绕过 DPS 桶） */
    private DamageSource gunBulletDamage(LivingEntity owner) {
        var key = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gun_bullet"));
        var holder = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(holder, this, owner);
    }
}
