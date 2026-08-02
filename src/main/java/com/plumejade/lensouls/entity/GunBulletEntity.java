package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.damage.ElementDamage;
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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class GunBulletEntity extends ThrowableItemProjectile {
    /** PartEntity 碰撞检测结果 */
    record PartHitResult(EntityHitResult hit, boolean isPart, PartEntity<?> part) {}

    private static final EntityDataAccessor<Byte> DATA_BULLET_TYPE =
            SynchedEntityData.defineId(GunBulletEntity.class, EntityDataSerializers.BYTE);

    private static final int COLLISION_SEGMENTS = 3; // 多段碰撞插值段数

    private double damage;
    private double armorPen;
    private UUID gunId;

    public UUID getGunId() { return gunId; }

    public GunBulletEntity(EntityType<? extends GunBulletEntity> type, Level level) {
        super(type, level);
    }

    public GunBulletEntity(Level level, LivingEntity shooter, int bulletType, double damage, double armorPen, UUID gunId) {
        super(ModEntities.GUN_BULLET.get(), level);
        setOwner(shooter);
        this.damage = damage;
        this.armorPen = armorPen;
        this.gunId = gunId;
        entityData.set(DATA_BULLET_TYPE, (byte) bulletType);
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

        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);

        if (!level().isClientSide) {
            Vec3 newPos = position();

            // ========== 碰撞检测：整条弹道搜索，PartEntity 优先，有部件不查主 AABB ==========
            Vec3 end = oldPos.add(vel);
            double margin = Math.max(1.0, vel.length());
            AABB fullPathBox = getBoundingBox().move(oldPos.subtract(newPos))
                    .expandTowards(vel).inflate(margin);

            PartHitResult best = null;
            double bestDist = Double.MAX_VALUE;

            for (Entity target : level().getEntities(this, fullPathBox, this::canHitEntity)) {
                PartHitResult r = checkEntity(target, oldPos, end);
                if (r != null) {
                    double d = r.hit().getLocation().distanceToSqr(oldPos);
                    if (d < bestDist) { bestDist = d; best = r; }
                }
            }

            if (best != null) {
                if (best.isPart() && best.part() != null) {
                    handlePartEntityHit(best.part());
                } else {
                    onHit(best.hit());
                }
                return;
            }

            if (tickCount > 200) discard();
        }
    }

    /**
     * 检测一个实体的碰撞。
     * 核心原则：有 PartEntity 的实体永不回退查主实体 AABB。
     */
    @Nullable
    private PartHitResult checkEntity(Entity target, Vec3 origin, Vec3 end) {
        PartEntity<?>[] parts = target.getParts();
        boolean hasParts = parts != null && parts.length > 0;

        // 1) 先查 PartEntity
        if (hasParts) {
            for (PartEntity<?> part : parts) {
                if (part == null || !part.isAlive()) continue;
                AABB box = part.getBoundingBox();
                double minDim = Math.min(Math.min(box.getXsize(), box.getYsize()), box.getZsize());
                if (minDim < 0.6) box = box.inflate(0.3);
                Optional<Vec3> clip = box.clip(origin, end);
                if (clip.isPresent()) {
                    return new PartHitResult(new EntityHitResult(target, clip.get()), true, part);
                }
                if (box.contains(origin)) {
                    return new PartHitResult(new EntityHitResult(target, origin), true, part);
                }
            }
            // 关键：有 PartEntity 但没命中任何部件 → 返回 null，让子弹继续飞
            // 永不回退查主实体 AABB（避免命中九头蛇大框后被 hurtServer 吞伤害）
            return null;
        }

        // 2) 无 PartEntity → 直接查主实体碰撞箱
        AABB box = target.getBoundingBox();
        double minDim = Math.min(Math.min(box.getXsize(), box.getYsize()), box.getZsize());
        if (minDim < 0.6) box = box.inflate(0.3);
        Optional<Vec3> clip = box.clip(origin, end);
        if (clip.isPresent()) {
            return new PartHitResult(new EntityHitResult(target, clip.get()), false, null);
        }
        if (box.contains(origin)) {
            return new PartHitResult(new EntityHitResult(target, origin), false, null);
        }
        return null;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target.equals(getOwner())) { discard(); return; }
        if (level().isClientSide) { discard(); return; }

        // PartEntity 分支：多部件 BOSS（九头蛇、末影龙等）的子碰撞体
        if (target instanceof PartEntity<?> part) {
            handlePartEntityHit(part);
            return;
        }

        if (!(target instanceof LivingEntity living)) { discard(); return; }

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

        playHitParticles(target);
        discard();
    }

    /**
     * 命中 PartEntity（多部件 BOSS 的子体）时的处理。
     * 不执行 LivingEntity 专属效果（治疗/点燃/牵引），仅应用伤害 + 粒子。
     */
    private void handlePartEntityHit(PartEntity<?> part) {
        if (level().isClientSide) { discard(); return; }

        float finalDamage = (float) damage;
        DamageSource gunSource = gunBulletDamage(null);

        // 重置部件和父实体无敌时间（PartEntity 委托到父实体 hurtServer）
        part.invulnerableTime = 0;
        Entity parent = part.getParent();
        if (parent != null) parent.invulnerableTime = 0;

        part.hurt(gunSource, finalDamage);

        playHitParticles(part);
        discard();
    }

    /** 发射命中粒子（按弹药类型：主世界→亮绿，地狱→橙，末地→紫） */
    private void playHitParticles(Entity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        byte type = entityData.get(DATA_BULLET_TYPE);
        var pType = switch (type) {
            case 0 -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK.get();      // 亮绿
            case 2 -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_PURPLE.get(); // 紫
            default -> com.plumejade.lensouls.particle.ModParticleTypes.HIT_SPARK_ORANGE.get(); // 橙
        };
        sl.sendParticles(pType, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                9, 0.5, 0.3, 0.5, 0.15);
    }

    public byte getBulletType() { return entityData.get(DATA_BULLET_TYPE); }

    /** 穿甲值（百分点，0~80，config dgBase/dgMaxArmorPen） */
    public double getArmorPen() { return armorPen; }

    /** 弹药类型→元素映射（0=土, 1=火, 2=末影） */
    public static ElementDamage getBulletElement(byte bulletType) {
        return switch (bulletType) {
            case 0 -> ElementDamage.EARTH;
            case 1 -> ElementDamage.FIRE;
            case 2 -> ElementDamage.ENDER;
            default -> null;
        };
    }
    @Override public boolean isNoGravity() { return true; }
    @Override protected boolean canHitEntity(Entity target) {
        if (!(target instanceof LivingEntity) || target.equals(getOwner())) return false;
        // 跳过友方单位：其他玩家、自己的宠物/坐骑、同盟实体
        return !isFriendlyTarget(target, getOwner());
    }

    /** 友方单位判定：其他玩家、驯养宠物（owned）、自身骑乘关系、同盟实体 */
    private static boolean isFriendlyTarget(Entity target, Entity owner) {
        if (target instanceof Player) return true;
        if (owner instanceof LivingEntity lo) {
            if (target instanceof TamableAnimal tame && tame.isOwnedBy(lo)) return true;
            if (owner.getVehicle() == target || target.getVehicle() == owner) return true;
        }
        return owner != null && target.isAlliedTo(owner);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) { super.readAdditionalSaveData(tag); damage = tag.getDouble("Damage"); armorPen = tag.getDouble("ArmorPen"); if (tag.contains("GunId")) { gunId = tag.getUUID("GunId"); } }
    @Override public void addAdditionalSaveData(CompoundTag tag) { super.addAdditionalSaveData(tag); tag.putDouble("Damage", damage); tag.putDouble("ArmorPen", armorPen); if (gunId != null) { tag.putUUID("GunId", gunId); } }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 4096; }

    /** 创建 lensouls:gun_bullet 伤害源（带 cataclysm:bypasses_hurt_time 标签，绕过 DPS 桶） */
    private DamageSource gunBulletDamage(LivingEntity owner) {
        var key = ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "gun_bullet"));
        var holder = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(holder, this, owner);
    }
}
