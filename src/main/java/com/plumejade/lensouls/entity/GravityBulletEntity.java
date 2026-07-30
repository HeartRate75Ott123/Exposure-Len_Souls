package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.GravityGunItem;
import com.plumejade.lensouls.item.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;

public class GravityBulletEntity extends ThrowableItemProjectile {
    private static final int COLLISION_SEGMENTS = 5;
    private static final int TIMEOUT_TICKS = 100;

    private static final EntityDataAccessor<Boolean> DATA_HAS_HIT =
            SynchedEntityData.defineId(GravityBulletEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HIT_TARGET_ID =
            SynchedEntityData.defineId(GravityBulletEntity.class, EntityDataSerializers.INT);

    private String gunUuid;
    private boolean hasHit;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HAS_HIT, false);
        builder.define(DATA_HIT_TARGET_ID, -1);
    }

    /** 客户端获取被牵引目标 */
    @javax.annotation.Nullable
    public LivingEntity getClientHitTarget() {
        int id = entityData.get(DATA_HIT_TARGET_ID);
        if (id == -1) return null;
        Entity e = level().getEntity(id);
        return e instanceof LivingEntity le ? le : null;
    }

    private LivingEntity hitTarget;
    private int hitTick;
    private int pullPhase;

    public GravityBulletEntity(EntityType<? extends GravityBulletEntity> type, Level level) {
        super(type, level);
    }

    public GravityBulletEntity(Level level, LivingEntity shooter, String gunUuid) {
        super(ModEntities.GRAVITY_BULLET.get(), level);
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.gunUuid = gunUuid;
    }

    // ======================== NBT ========================

    private static final String TAG_GUN_UUID = "GravityGunUuid";

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (gunUuid != null) tag.putString(TAG_GUN_UUID, gunUuid);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_GUN_UUID)) gunUuid = tag.getString(TAG_GUN_UUID);
    }

    // ======================== 基础 ========================

    @Override
    protected Item getDefaultItem() { return ModItems.DRAG_BULLET.get(); }

    // ======================== 每 tick ========================

    @Override
    public void tick() {
        Vec3 oldPos = position();
        Vec3 vel = getDeltaMovement();

        if (!level().isClientSide) {
            if (!hasHit) {
                setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
                Vec3 newPos = position();

                // 方块碰撞
                if (level().clip(new ClipContext(oldPos, newPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                        .getType() != HitResult.Type.MISS) {
                    discard();
                    return;
                }

                // 全轨迹射线检测（PartEntity 优先）
                double margin = Math.max(1.0, vel.length());
                AABB searchBox = getBoundingBox().move(oldPos.subtract(newPos)).expandTowards(vel).inflate(margin);
                double bestDist = Double.MAX_VALUE;
                EntityHitResult bestHit = null;

                for (Entity target : level().getEntities(this, searchBox, this::canHitEntity)) {
                    EntityHitResult hit = findClosestHit(target, oldPos, vel);
                    if (hit != null) {
                        double d = hit.getLocation().distanceToSqr(oldPos);
                        if (d < bestDist) { bestDist = d; bestHit = hit; }
                    }
                }
                if (bestHit != null) { onHit(bestHit); return; }

                // 超时
                if (tickCount > TIMEOUT_TICKS) { discard(); }
                return;
            }

            // ── 已命中：牵引逻辑（带碰撞箱相交检测）──
            if (hasHit && hitTarget != null) {
                if (!hitTarget.isAlive()) { discard(); return; }
                if (!(getOwner() instanceof Player player) || !player.isAlive()) { discard(); return; }
                if (!isActivePull(player)) { discard(); return; }

                // 计算牵引方向和距离
                Vec3 toPlayer = player.position().subtract(hitTarget.position());
                double dist = toPlayer.length();

                if (dist > 1.5) {
                    // ── 距离较远：主动牵引，带多段碰撞检测 ──
                    Vec3 pullDir = toPlayer.normalize();
                    double strength = Math.min(1.5, dist * 0.3) * Config.GG_PULL_FORCE.get();
                    Vec3 pull = pullDir.scale(strength);

                    // 多段碰撞检测：牵引轨迹上是否进入玩家缓冲区域
                    AABB safePlayerBox = player.getBoundingBox().inflate(1.0);
                    AABB targetBox = hitTarget.getBoundingBox();
                    int segments = 5;
                    Vec3 step = pull.scale(1.0 / segments);
                    boolean blocked = false;

                    for (int s = 0; s < segments; s++) {
                        if (targetBox.move(step.scale(s + 1)).intersects(safePlayerBox)) {
                            // 将在第 s 段进入玩家 1 格缓冲区内 → 提前停止
                            if (s > 0) {
                                hitTarget.setDeltaMovement(step.scale(s));
                            } else {
                                hitTarget.setDeltaMovement(Vec3.ZERO);
                            }
                            pullPhase = 2;
                            blocked = true;
                            break;
                        }
                    }

                    if (!blocked) {
                        // 全程安全，应用完整牵引
                        hitTarget.setDeltaMovement(pull);
                    }
                    hitTarget.hurtMarked = true;
                } else {
                    // ── 已在玩家 1.5 格内 → 停止牵引 ──
                    pullPhase = 2;
                    hitTarget.setDeltaMovement(Vec3.ZERO);
                    hitTarget.hurtMarked = true;
                }
                return;
            }
        }
    }

    /** 检查引力枪 ActiveBulletId 是否仍指向本子弹 */
    private boolean isActivePull(Player player) {
        if (gunUuid == null) return false;
        ServerPlayer sp = (ServerPlayer) player;
        for (ItemStack stack : sp.getInventory().items) {
            if (stack.getItem() instanceof GravityGunItem) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (gunUuid.equals(tag.getString("GravityGunItemId"))) {
                    return tag.getInt("ActiveBulletId") == getId();
                }
            }
        }
        ItemStack offhand = sp.getInventory().offhand.get(0);
        if (offhand.getItem() instanceof GravityGunItem) {
            CompoundTag tag = offhand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (gunUuid.equals(tag.getString("GravityGunItemId"))) {
                return tag.getInt("ActiveBulletId") == getId();
            }
        }
        return false;
    }

    // ======================== 命中 ========================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target.equals(getOwner()) || level().isClientSide) return;

        // PartEntity 命中 - 委托到父实体
        if (target instanceof PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent instanceof LivingEntity living) {
                parent.invulnerableTime = 0;
                part.invulnerableTime = 0;
                hasHit = true;
                entityData.set(DATA_HAS_HIT, true);
                entityData.set(DATA_HIT_TARGET_ID, living.getId());
                hitTarget = living;
                hitTick = tickCount;
                pullPhase = 1;
                // 写 ActiveBulletId 到引力枪物品
                if (getOwner() instanceof ServerPlayer sp) {
                    for (ItemStack s : sp.getInventory().items) {
                        if (s.getItem() instanceof com.plumejade.lensouls.item.GravityGunItem) {
                            net.minecraft.nbt.CompoundTag st = s.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
                            if (gunUuid.equals(st.getString("GravityGunItemId"))) {
                                st.putInt("ActiveBulletId", getId());
                                s.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(st));
                                break;
                            }
                        }
                    }
                }
            }
            discard();
            return;
        }

        if (!(target instanceof LivingEntity living)) { discard(); return; }

        Entity owner = getOwner();
        if (owner == null) { discard(); return; }


        // 伤害
        if (owner instanceof LivingEntity lo) {
            living.invulnerableTime = 0;
            living.hurt(damageSources().playerAttack((Player) lo), 1.0f);
        }

        // 标记命中 + 同步到客户端（隐藏弹射物 + 目标 ID）
        hasHit = true;
        entityData.set(DATA_HAS_HIT, true);
        entityData.set(DATA_HIT_TARGET_ID, living.getId());
        hitTarget = living;
        hitTick = tickCount;
        pullPhase = 1;

        // 写入 ActiveBulletId
        if (owner instanceof ServerPlayer sp) {
            boolean found = false;
            for (ItemStack s : sp.getInventory().items) {
                if (s.getItem() instanceof GravityGunItem) {
                    CompoundTag st = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (gunUuid.equals(st.getString("GravityGunItemId"))) {
                        st.putInt("ActiveBulletId", getId());
                        s.set(DataComponents.CUSTOM_DATA, CustomData.of(st));
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                ItemStack offhand = sp.getInventory().offhand.get(0);
                if (offhand.getItem() instanceof GravityGunItem) {
                    CompoundTag st = offhand.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                    if (gunUuid.equals(st.getString("GravityGunItemId"))) {
                        st.putInt("ActiveBulletId", getId());
                        offhand.set(DataComponents.CUSTOM_DATA, CustomData.of(st));
                    }
                }
            }
        }
    }

    // ======================== 物理 ========================

    @Override public boolean isNoGravity() { return true; }
    @Override public boolean shouldRender(double x, double y, double z) { return !entityData.get(DATA_HAS_HIT) && super.shouldRender(x, y, z); }
    @Override protected boolean canHitEntity(Entity target) { return target instanceof LivingEntity && !target.equals(getOwner()); }
    /** PartEntity 优先检测，有 Parts 绝不回退父 AABB */
    @Nullable
    private EntityHitResult findClosestHit(Entity target, Vec3 origin, Vec3 vel) {
        Vec3 end = origin.add(vel);
        PartEntity<?>[] parts = target.getParts();
        if (parts != null) {
            EntityHitResult best = null;
            double bestD = Double.MAX_VALUE;
            for (PartEntity<?> part : parts) {
                if (part == null || !part.isAlive()) continue;
                var clip = part.getBoundingBox().clip(origin, end);
                if (clip.isPresent()) {
                    double d = clip.get().distanceToSqr(origin);
                    if (d < bestD) { bestD = d; best = new EntityHitResult(part, clip.get()); }
                }
                if (part.getBoundingBox().contains(origin)) return new EntityHitResult(part, origin);
            }
            return best;
        }
        AABB box = target.getBoundingBox();
        double minDim = Math.min(Math.min(box.getXsize(), box.getYsize()), box.getZsize());
        if (minDim < 0.6) box = box.inflate(0.4);
        var clip = box.clip(origin, end);
        if (clip.isPresent()) return new EntityHitResult(target, clip.get());
        if (box.contains(origin)) return new EntityHitResult(target, origin);
        return null;
    }

    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 4096; }
}
