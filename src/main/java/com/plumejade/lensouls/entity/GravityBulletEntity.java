package com.plumejade.lensouls.entity;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.item.GravityGunItem;
import com.plumejade.lensouls.handler.GravityTetherManager;
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

        // ── 位移**两端都做**，与同项目的次元枪 `GunBulletEntity.tick` 完全一致 ──
        // 这是能正常工作的参照实现：客户端每 tick 自己前进一格（服务端的每 3 tick 位置包只当校正，
        // 因为两端算的是同一套等速直线，校正包里就是同一个坐标，不会跳）。
        // 反面教材：只在服务端移动 + 靠 hasImpulse 补包，实测仍顿挫（服务端发包/客户端补间
        // 之间的相位差无法消除）；而"客户端单方面预测、服务端另行移动"会在命中判定上产生分歧。
        setPos(getX() + vel.x, getY() + vel.y, getZ() + vel.z);

        if (level().isClientSide) return;

        if (!level().isClientSide) {
            if (!hasHit) {
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
            }
        }
    }

    // ======================== 命中 =========================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        if (target.equals(getOwner()) || level().isClientSide) return;

        LivingEntity living = null;
        if (target instanceof PartEntity<?> part) {
            // 多部件 BOSS：命中部件的碰撞盒，牵引挂到父实体
            if (part.getParent() instanceof LivingEntity parent) living = parent;
            part.invulnerableTime = 0;
        } else if (target instanceof LivingEntity le) {
            living = le;
        }
        if (living == null) { discard(); return; }

        // 1 点伤害保持原样（结算口径不变）
        if (getOwner() instanceof LivingEntity lo) {
            living.invulnerableTime = 0;
            living.hurt(damageSources().playerAttack((Player) lo), 1.0f);
        }

        // 牵引关系交给服务端管理器（2026-10-31 重构：不再靠这颗子弹支撑整条牵引）。
        // 登记完立即 discard —— 子弹只负责"把牵引挂上去"，之后它的存在与否与本功能无关。
        if (getOwner() instanceof ServerPlayer sp) {
            GravityTetherManager.start(sp, living);
        }
        discard();
    }

    // ======================== 物理 ========================

    @Override public boolean isNoGravity() { return true; }
    @Override public boolean shouldRender(double x, double y, double z) { return !entityData.get(DATA_HAS_HIT) && super.shouldRender(x, y, z); }
    @Override protected boolean canHitEntity(Entity target) {
        if (target.equals(getOwner())) return false;
        // 尸体（已死实体）不拦截子弹，让其穿过继续飞行
        if (!target.isAlive()) return false;
        // 多部件子体（娜迦身子/九头蛇头等 PartEntity，非 LivingEntity）：NeoForge getEntities 会返回它们，需放行
        if (target instanceof PartEntity<?>) return true;
        return target instanceof LivingEntity;
    }
    /**
     * 单实体碰撞检测。
     * <p>
     * <b>前半段与次元枪 {@code GunBulletEntity.checkEntity} 同口径</b>：有部件先查部件盒、
     * 细小部件（最小维度 &lt; 0.6）补 0.3、起点在盒内直接算命中。
     * <p>
     * <b>但引力枪比次元枪多一条"父实体兜底"</b>（2026-10-31 实测：原版雪球能打中九头蛇/娜迦，
     * 次元枪能，引力枪不能 —— 说明这两只本体的大 AABB 本来就是可命中的，
     * 而"有部件就绝不回退父 AABB"是次元枪为了不被 {@code hurtServer} 吞伤害才定的规矩）。
     * 引力枪的语义是<b>抓取</b>：哪怕这 1 点伤害被吞，只要牵引挂上就算成功，
     * 所以部件一个都没中时再试一次本体大 AABB（补 0.4）—— 与雪球完全一致的可命中面。
     * 顺序仍是"部件优先、本体兜底"，所以打头部/体节依然精确挂到父实体身上。
     *
     * @return 可能是部件本身（{@link PartEntity}），由 {@link #onHitEntity} 归一到父实体做牵引
     */
    @Nullable
    private EntityHitResult findClosestHit(Entity target, Vec3 origin, Vec3 vel) {
        Vec3 end = origin.add(vel);

        // 1) 本体大 AABB：先算出来备用（兜底用），同时也覆盖"没有部件"的绝大多数实体
        AABB mainBox = target.getBoundingBox();
        double mainMinDim = Math.min(Math.min(mainBox.getXsize(), mainBox.getYsize()), mainBox.getZsize());
        if (mainMinDim < 0.6) mainBox = mainBox.inflate(0.4);

        // 2) 部件优先（精确命中：头、体节、翅膀…）
        PartEntity<?>[] parts = target.getParts();
        if (parts != null && parts.length > 0) {
            EntityHitResult best = null;
            double bestD = Double.MAX_VALUE;
            for (PartEntity<?> part : parts) {
                if (part == null || !part.isAlive()) continue;
                AABB box = part.getBoundingBox();
                double minDim = Math.min(Math.min(box.getXsize(), box.getYsize()), box.getZsize());
                if (minDim < 0.6) box = box.inflate(0.3);
                var clip = box.clip(origin, end);
                if (clip.isPresent()) {
                    double d = clip.get().distanceToSqr(origin);
                    if (d < bestD) { bestD = d; best = new EntityHitResult(part, clip.get()); }
                } else if (box.contains(origin)) {
                    return new EntityHitResult(part, origin);
                }
            }
            if (best != null) return best;
            // 部件都没中 → 落到下面的本体兜底（次元枪在这里 return null，引力枪要抓取所以继续）
        }

        // 3) 本体兜底
        var clip = mainBox.clip(origin, end);
        if (clip.isPresent()) return new EntityHitResult(target, clip.get());
        if (mainBox.contains(origin)) return new EntityHitResult(target, origin);
        return null;
    }

    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 4096; }
}
