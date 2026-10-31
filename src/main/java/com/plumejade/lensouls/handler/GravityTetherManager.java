package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.ability.network.GravityTetherSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 引力枪牵引管理器（服务端单例）。
 * <p>
 * <b>为什么要有它</b>（2026-10-31 重构）：以前「牵引」挂在一颗子弹实体上，客户端也靠那颗子弹决定
 * 画不画光束。玩家一跑快，子弹被移出客户端跟踪范围甚至移除重建，光束先消失、最后连牵引都断。
 * 现在改成 <b>关系</b>（照原版拴绳 {@code Leashable} + {@code ClientboundSetEntityLinkPacket} 的思路）：
 * <ul>
 *   <li>命中那一刻登记 {@code targetId -> ownerId}，子弹随即 {@code discard()}（使命已完成）；</li>
 *   <li>牵引物理每 tick 在这里执行（原逻辑从 {@code GravityBulletEntity} 平移而来）；</li>
 *   <li>关系变化时发一次 {@link GravityTetherSyncPacket}（全量配对表），客户端按关系渲染，
 *       端点用两端实体的 partialTick 插值现算 —— 与子弹、跟踪范围、区块加载全部解耦。</li>
 * </ul>
 * 结束方式保持原样：再次右键取消、目标死亡、拥有者死亡/离线、换维度。
 */
public final class GravityTetherManager {

    /** 一条牵引：目标 id + 拥有者 id + 所在维度（实体查找必须限定维度，否则下界/末地都查不到）。 */
    private record Link(int targetId, int ownerId, ServerLevel level) {
    }

    private static final Map<Integer, Link> LINKS = new LinkedHashMap<>();   // targetId -> Link

    private GravityTetherManager() {
    }

    /** 开始牵引（命中的那一 tick 调用）。 */
    public static void start(ServerPlayer owner, LivingEntity target) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        LINKS.put(target.getId(), new Link(target.getId(), owner.getId(), level));
        broadcast(level);
    }

    /** 该玩家是否正在牵引某物。 */
    public static boolean isPulling(ServerPlayer owner) {
        for (Link l : LINKS.values()) {
            if (l.ownerId() == owner.getId()) return true;
        }
        return false;
    }

    /** 按拥有者解除牵引（右键取消 / 死亡 / 换维度）。 */
    public static void stopByOwner(ServerPlayer owner) {
        boolean changed = LINKS.values().removeIf(l -> l.ownerId() == owner.getId());
        if (changed && owner.level() instanceof ServerLevel level) broadcast(level);
    }

    public static void clear() {
        LINKS.clear();
    }

    /** 玩家重新进服/换维度回来时补一次快照，避免"关系还在但客户端没收到"。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) sendSnapshotTo(sp);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LINKS.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (LINKS.isEmpty()) return;

        boolean changed = false;
        Iterator<Map.Entry<Integer, Link>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            Link link = it.next().getValue();
            ServerLevel level = link.level();

            if (!(level.getEntity(link.ownerId()) instanceof ServerPlayer owner) || !owner.isAlive()) {
                it.remove();
                changed = true;
                continue;
            }
            // 换维度：牵引在物理上不再成立，直接结束
            if (owner.level() != level) {
                it.remove();
                changed = true;
                continue;
            }
            if (!(level.getEntity(link.targetId()) instanceof LivingEntity target) || !target.isAlive()) {
                it.remove();
                changed = true;
                continue;
            }
            applyPull(owner, target);
        }

        if (changed) {
            for (ServerLevel level : event.getServer().getAllLevels()) broadcast(level);
        }
    }

    /** 牵引物理：从 {@code GravityBulletEntity.tick} 的牵引分支平移过来，判定与参数完全一致。 */
    private static void applyPull(ServerPlayer player, LivingEntity target) {
        Vec3 toPlayer = player.position().subtract(target.position());
        double dist = toPlayer.length();

        if (dist <= 1.5) {
            target.setDeltaMovement(Vec3.ZERO);
            target.hurtMarked = true;
            return;
        }

        Vec3 pull = toPlayer.normalize().scale(Math.min(1.5, dist * 0.3) * Config.GG_PULL_FORCE.get());

        // 多段碰撞检测：牵引轨迹上是否进入玩家 1 格缓冲区
        AABB safePlayerBox = player.getBoundingBox().inflate(1.0);
        AABB targetBox = target.getBoundingBox();
        int segments = 5;
        Vec3 step = pull.scale(1.0 / segments);

        for (int s = 0; s < segments; s++) {
            if (targetBox.move(step.scale(s + 1)).intersects(safePlayerBox)) {
                target.setDeltaMovement(s > 0 ? step.scale(s) : Vec3.ZERO);
                target.hurtMarked = true;
                return;
            }
        }
        target.setDeltaMovement(pull);
        target.hurtMarked = true;
    }

    /** 把当前关系全量推给该维度的玩家（关系变化时才调用）。 */
    private static void broadcast(ServerLevel level) {
        Map<Integer, Integer> snapshot = new LinkedHashMap<>();
        for (Link l : LINKS.values()) {
            if (l.level() == level) snapshot.put(l.targetId(), l.ownerId());
        }
        GravityTetherSyncPacket packet = new GravityTetherSyncPacket(snapshot);
        for (ServerPlayer p : level.players()) {
            PacketDistributor.sendToPlayer(p, packet);
        }
    }

    /** 只给当前维度发一次快照（重连/换维度回来时用）。 */
    public static void sendSnapshotTo(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) broadcast(level);
    }

    public static int linkCount() {
        return LINKS.size();
    }
}
