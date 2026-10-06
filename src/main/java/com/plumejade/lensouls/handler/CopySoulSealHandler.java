package com.plumejade.lensouls.handler;

import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.component.ModDataComponents;
import com.plumejade.lensouls.item.CopySoulItem;
import com.plumejade.lensouls.reinforce.BeyondDimensionsCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 复制之魂「禁复制封印」的全容器扫描器。
 * <p>
 * 语义（沿用 {@link CopySoulItem} 的栈级封印设计）：佩戴禁复制羽毛（④ 折翼沉渊）期间，
 * 随身的复制之魂自动打上 {@link ModDataComponents#COPY_SOUL_SEALED}，配方层见到即拒绝匹配；
 * 摘下羽毛 1~10 tick 内自动解封。
 * <p>
 * <b>2026-09 重设计</b>：① 荒厄遗咒与 ③ 元素觉醒者已不再封印复制之魂（旧版的
 * 「元素觉醒 / 铁人」两路已移除），只有 ④ 仍在封印范围内。
 * <p>
 * 原实现只覆盖「物品栏槽位里的复制之魂」（靠 {@code ItemStack#inventoryTick}）。玩家实测的漏洞是：
 * 收在<b>精妙背包内容物</b>与<b>超越维度终端</b>里的复制之魂打不上标签——而这两处恰恰是玩家最常
 * 囤魂的地方，于是「戴上羽毛 → 从终端取魂 → 复制」这条绕行路线仍然可用。
 * <p>
 * 本类补齐三处：
 * <ol>
 *   <li>玩家物品栏 41 格（保留 {@code inventoryTick} 的即时路径，这里只是兜底对齐）；</li>
 *   <li>背包类容器的<b>内容物</b>：物品栏/饰品栏里任何带 {@code Capabilities.ItemHandler.ITEM}
 *       的物品（精妙背包、精妙核心衍生容器等），逐槽改写；</li>
 *   <li>超越维度网络存储 + 随身物质压缩球（纯反射，见 {@link BeyondDimensionsCompat}）。</li>
 * </ol>
 * <b>性能</b>（需求点名「注意性能开销」）：
 * <ul>
 *   <li>全量扫描每 {@link #SWEEP_INTERVAL_TICKS} tick 才有一次，且<b>只在需要时</b>发生：
 *       玩家戴着禁复制羽毛（要封）或上一次确实封过东西（要解封）；平时一个判断就返回；</li>
 *   <li>封印状态没变就不写组件——写组件会触发物品同步，是这里最贵的动作；</li>
 *   <li>超越维度终端动辄上万条，封装判定只读 {@code KeyAmount} 的 key 与数量，命中才写回。</li>
 * </ul>
 */
public final class CopySoulSealHandler {

    /** 容器扫描间隔（tick）：0.5 秒一次 */
    private static final int SWEEP_INTERVAL_TICKS = 20;
    /** 玩家「当前是否禁复制」缓存刷新间隔（tick）：{@code inventoryTick} 每 tick 读缓存，避免每槽每 tick 查 Curios */
    private static final int FLAG_INTERVAL_TICKS = 10;

    /** UUID → 是否处于禁复制状态（服务端缓存） */
    private static final Map<UUID, Boolean> SEAL_FLAG = new ConcurrentHashMap<>();
    /** 曾经在「容器里」封过东西的玩家：摘羽毛后需要再扫一次把封印摘掉 */
    private static final Set<UUID> SEALED_IN_CONTAINERS = ConcurrentHashMap.newKeySet();
    /** 扫描可重入保护：写物品组件 / 写容器内容物 / 写网络都会触发同步与回调 */
    private static final java.util.concurrent.atomic.AtomicBoolean SWEEPING =
            new java.util.concurrent.atomic.AtomicBoolean();

    // ========== 对外 API ==========

    /**
     * 玩家当前是否处于「禁复制」状态。
     * <p>
     * {@code ItemStack#inventoryTick} 每 tick 都会问一次（每槽一次），因此这里读缓存；
     * 缓存由 {@link #onPlayerTick} 每 {@link #FLAG_INTERVAL_TICKS} tick 刷新；
     * 缓存未就绪（刚上线/刚装羽毛）时现场算一次。
     */
    public static boolean shouldSeal(Player player) {
        Boolean cached = SEAL_FLAG.get(player.getUUID());
        if (cached != null) return cached;
        boolean seal = computeSeal(player);
        SEAL_FLAG.put(player.getUUID(), seal);
        return seal;
    }

    /**
     * 玩家是否戴着任一种「禁复制羽毛」。
     * <p>
     * 超越维度终端是<b>共享网络</b>：解封方向必须逐个检查在线成员，
     * 所以这个判定要对别的玩家也能调用（见 {@code BeyondDimensionsCompat.sealCopySouls}）。
     */
    public static boolean wearsForbiddenFeather(Player player) {
        if (player == null) return false;
        // 新改案：① 荒厄·封器 与 ③ 元素觉醒者都已移除「禁止复制之魂」相关内容，
        // 目前只有 ④ 折翼沉渊（代码现状，未改）仍封印复制之魂。
        return FeatherAbyssHandler.hasAbyss(player);
    }

    // ========== 事件 ==========

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int phase = player.tickCount % SWEEP_INTERVAL_TICKS;

        boolean seal;
        if (phase == 0) {
            seal = computeSeal(player);
            SEAL_FLAG.put(player.getUUID(), seal);
        } else if (phase % FLAG_INTERVAL_TICKS == 0) {
            // 羽毛佩戴状态变化最多 10 tick 内跟上
            seal = computeSeal(player);
            SEAL_FLAG.put(player.getUUID(), seal);
        } else {
            seal = shouldSeal(player);
        }

        if (phase != 0) return;
        // 只有「要封」或「上次确实封过」才值得扫容器；否则整段跳过
        if (!seal && !SEALED_IN_CONTAINERS.contains(player.getUUID())) return;
        sweep(player, seal);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    /**
     * 饰品槽位变化：羽毛的穿戴/摘下立即对齐一次全容器扫描，不用等 20 tick 的周期档。
     * <p>
     * Curios 是在实体 tick 里逐 tick 比对上一刻槽位内容才派发本事件的，所以最多晚 1 tick；
     * 状态没变时（其它饰品的增删）直接返回，不做任何扫描。
     */
    @SubscribeEvent
    public static void onCurioChanged(top.theillusivec4.curios.api.event.CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean seal = computeSeal(player);
        Boolean previous = SEAL_FLAG.put(player.getUUID(), seal);
        if (previous != null && previous == seal) return;
        sweep(player, seal);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // 死亡重生：新实体新属性，缓存跟着重算
        clear(event.getEntity().getUUID());
    }

    /** 清理玩家缓存（登出/重生/切档） */
    public static void clear(UUID uuid) {
        SEAL_FLAG.remove(uuid);
        SEALED_IN_CONTAINERS.remove(uuid);
    }

    // ========== 内部实现 ==========

    /**
     * 取容器物品的内容物 handler：优先用 NeoForge 能力，能力给不出槽位时退回<b>精妙背包</b>自己的
     * wrapper（纯反射，模组缺席/改版一律静默跳过）。
     * <p>
     * 为什么要兜底：精妙背包的能力在 {@code getContentsUuid()} 为空时返回 0 槽的
     * {@code EmptyItemHandler}，而那个判定<em>只读不建</em>——个别路径（历史存档迁移、
     * 自动化塞入）可能出现「有内容物但没有 UUID 组件」的背包。走它自己的
     * {@code BackpackWrapper.fromStack(stack).getInventoryHandler()} 会惰性补上 UUID，
     * 是唯一能保证读到内容物的入口。
     */
    private static IItemHandler handlerOf(ItemStack carrier) {
        IItemHandler viaCapability = carrier.getCapability(Capabilities.ItemHandler.ITEM);
        if (viaCapability != null && viaCapability.getSlots() > 0) return viaCapability;
        // 只有精妙背包需要 wrapper 兜底：其它容器模组的能力路径本来就够，
        // 而每个非容器物品都反射建一次 wrapper 在 41 格 × 每秒的节奏下是纯浪费
        if (!isSophisticatedBackpack(carrier)) return viaCapability;
        IItemHandler viaWrapper = sophisticatedBackpackHandler(carrier);
        return viaWrapper != null ? viaWrapper : viaCapability;
    }

    /** 粗筛：物品注册名在 `sophisticatedbackpacks` 命名空间下（免去给每个物品建 wrapper）。 */
    private static boolean isSophisticatedBackpack(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "sophisticatedbackpacks".equals(id.getNamespace());
    }

    private static volatile Method sbFromStack;
    private static volatile Method sbInventoryHandler;
    private static volatile boolean sbResolved;

    private static IItemHandler sophisticatedBackpackHandler(ItemStack carrier) {
        if (!net.neoforged.fml.ModList.get().isLoaded("sophisticatedbackpacks")) return null;
        try {
            if (!sbResolved) {
                sbResolved = true;
                Class<?> wrapperCls = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
                sbFromStack = wrapperCls.getMethod("fromStack", ItemStack.class);
                sbInventoryHandler = Class.forName(
                                "net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper")
                        .getMethod("getInventoryHandler");
            }
            if (sbFromStack == null || sbInventoryHandler == null) return null;
            Object wrapper = sbFromStack.invoke(null, carrier);
            if (wrapper == null) return null;
            Object handler = sbInventoryHandler.invoke(wrapper);
            return handler instanceof IItemHandler itemHandler ? itemHandler : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean computeSeal(Player player) {
        return wearsForbiddenFeather(player);
    }

    /** 扫一遍三处容器，按 {@code seal} 打/摘封印 */
    private static void sweep(ServerPlayer player, boolean seal) {
        // 可重入保护：本次扫描会写物品组件、容器内容物与超越维度网络，
        // 每一处都可能触发同步/回调（BD 的 onContentChanged、容器 setStackInSlot 等），
        // 若不设闸就会「扫描 → 写 → 回调 → 再扫描」地无限递归连发。
        if (!SWEEPING.compareAndSet(false, true)) return;

        boolean touchedContainers = false;
        try {
            // 1) 玩家物品栏 41 格（即时路径由 inventoryTick 负责，这里兜底）
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                applySeal(inventory.getItem(i), seal);
            }

            // 2) 物品栏 + 饰品栏里的「容器类物品」内容物（精妙背包等）
            List<ItemStack> carriers = new ArrayList<>(inventory.getContainerSize() + 8);
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!stack.isEmpty()) carriers.add(stack);
            }
            carriers.addAll(equippedCurios(player));
            for (ItemStack carrier : carriers) {
                if (applyInsideHandler(carrier, seal)) touchedContainers = true;
            }

            // 3) 超越维度：网络存储 + 随身物质压缩球
            try {
                if (BeyondDimensionsCompat.isLoaded() && BeyondDimensionsCompat.sealCopySouls(player, seal, carriers)) {
                    touchedContainers = true;
                }
            } catch (Throwable t) {
                LenSouls.LOGGER.warn("[CopySoul] 超越维度封印扫描失败（已忽略本次）", t);
            }
        } finally {
            // 标志维护必须在 finally 里：中途抛错时若漏掉，「摘掉羽毛后每 20 tick 继续扫」
            // 会成为永久状态（实测：摘除折翼后终端里的复制之魂仍被反复迁移）。
            if (seal && touchedContainers) {
                SEALED_IN_CONTAINERS.add(player.getUUID());
            } else if (!seal) {
                SEALED_IN_CONTAINERS.remove(player.getUUID());
            }
            SWEEPING.set(false);
        }
    }

    /**
     * 登录兜底：没戴禁复制羽毛时清一次历史封印。
     * <p>
     * 【1.5.26】终端里的封印魂不会自己解封：原来的「上次封过东西」标志只存在内存里，
     * 换会话/重登就丢，于是旧会话封印过的终端魂永远带着 {@code copy_soul_sealed} 躺着
     * （实测存档里同时存在封印版与未封印版两条复制之魂）。登录时做一次无羽毛扫描，
     * 把随身容器与（没有羽毛成员的）网络一起对齐。
     */
    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (wearsForbiddenFeather(player)) return;
        sweep(player, false);
    }

    private static List<ItemStack> equippedCurios(ServerPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            IItemHandlerModifiable equipped = handler.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                ItemStack stack = equipped.getStackInSlot(i);
                if (!stack.isEmpty()) stacks.add(stack);
            }
        });
        return stacks;
    }

    /** 容器类物品：遍历其 {@code Capabilities.ItemHandler.ITEM} 内容物并改写。返回是否真的改了东西。
     * <p>
     * 精妙背包（SophisticatedBackpacks）的内容物存在<b>关卡 SavedData</b> 里，槽位 NBT 是
     * 单独缓存的（{@code InventoryHandler.serializeNBT} 写的是 {@code stackNbts} 的 Tag），
     * 因此<b>必须 {@code setStackInSlot}</b> 回写才会落盘——就地改 {@code getStackInSlot}
     * 返回的实例只在当前会话看起来生效，重进世界就丢。
     * 同时「取能力」必须用槽位里的活 ItemStack：传副本会让对方新建一份 wrapper（其缓存是
     * ItemStack 身份语义），一个背包出现两份内存态会互相脏写。
     */
    private static boolean applyInsideHandler(ItemStack carrier, boolean seal) {
        IItemHandler handler = handlerOf(carrier);
        if (handler == null || handler.getSlots() <= 0) return false;
        boolean changed = false;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack inner = handler.getStackInSlot(i);
            if (inner.isEmpty() || !(inner.getItem() instanceof CopySoulItem)) continue;
            boolean sealed = inner.has(ModDataComponents.COPY_SOUL_SEALED.get());
            if (sealed == seal) continue;

            ItemStack updated = inner.copy();
            if (seal) {
                updated.set(ModDataComponents.COPY_SOUL_SEALED.get(), true);
            } else {
                updated.remove(ModDataComponents.COPY_SOUL_SEALED.get());
            }
            changed = true;
            if (handler instanceof IItemHandlerModifiable modifiable) {
                try {
                    modifiable.setStackInSlot(i, updated);
                    continue;
                } catch (RuntimeException ignored) {
                    // 只读/受限槽位：退回就地改写（活引用实现有效）
                }
            }
            if (seal) {
                inner.set(ModDataComponents.COPY_SOUL_SEALED.get(), true);
            } else {
                inner.remove(ModDataComponents.COPY_SOUL_SEALED.get());
            }
        }
        return changed;
    }

    /**
     * 按需给一个物品栈打/摘封印。
     *
     * @return 是否真的改动了该物品栈（没变化时不写组件——写组件会触发同步）
     */
    private static boolean applySeal(ItemStack stack, boolean seal) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof CopySoulItem)) return false;
        boolean sealed = stack.has(ModDataComponents.COPY_SOUL_SEALED.get());
        if (sealed == seal) return false;
        if (seal) {
            stack.set(ModDataComponents.COPY_SOUL_SEALED.get(), true);
        } else {
            stack.remove(ModDataComponents.COPY_SOUL_SEALED.get());
        }
        return true;
    }

    private CopySoulSealHandler() {
    }
}
