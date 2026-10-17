package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.entity.BossPhantomManager;
import com.plumejade.lensouls.util.AllyFilter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 幻灵（借体 BOSS）不破坏方块。
 *
 * <p><b>为什么需要</b>：借体驱动让真实 BOSS 实体跑自己的 AI，其中一部分 AI 会改地形——
 * 暮色九头蛇 {@code Hydra.destroyBlocksInAABB} 每 tick 按本体/尾/每个头的包围盒
 * {@code level.destroyBlock}，它的爆裂火球（{@code HydraMortar}）命中还会
 * {@code level.explode(..., ExplosionInteraction.MOB)}。幻灵只是「演出」，不该在玩家世界里挖洞。
 *
 * <p><b>两条拦截</b>：
 * <ol>
 *   <li>{@link EntityMobGriefingEvent}：TF 的 {@code destroyBlocksInAABB} 与
 *       {@code HydraMortar.detonate} 都先调 {@code EventHooks.canEntityGrief}。
 *       这一条覆盖所有走该 NeoForge 钩子的破坏（也顺带覆盖其它 BOSS 的同类逻辑）；</li>
 *   <li>{@link ExplosionEvent.Detonate}：把爆炸的<b>方块列表</b>清空——
 *       不阻止爆炸本身，实体伤害/击退照常（幻灵对玩家的伤害另有 PhantomDamageHandler 兜底）。
 *       这是给不查 {@code canEntityGrief} 的爆炸留的保险。</li>
 * </ol>
 * 归属判定统一走 {@link AllyFilter}：本体 / 召唤物 / 弹幕 owner 链；
 * 暮色的头部等 {@link PartEntity} 多部件再向上追一层父实体。
 */
public class PhantomGriefHandler {

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (!BossPhantomManager.getInstance().hasActive()) return;
        if (!event.canGrief()) return;
        if (isPhantomOwned(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide()) return;
        if (!BossPhantomManager.getInstance().hasActive()) return;
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null || !isPhantomOwned(source)) return;
        // 只清方块：爆炸伤害/击退保持原样
        event.getAffectedBlocks().clear();
    }

    /** 本体 / 召唤物 / 弹幕 owner 链 / 多部件父实体 → 是否属幻灵。 */
    private static boolean isPhantomOwned(Entity start) {
        Entity cur = start;
        for (int depth = 0; cur != null && depth < 8; depth++) {
            if (AllyFilter.isPhantomSource(cur)) return true;
            if (cur instanceof PartEntity<?> part) {
                Entity parent = part.getParent();
                if (parent == cur) return false;
                cur = parent;
            } else {
                cur = cur instanceof Projectile projectile ? projectile.getOwner() : null;
            }
        }
        return false;
    }
}
