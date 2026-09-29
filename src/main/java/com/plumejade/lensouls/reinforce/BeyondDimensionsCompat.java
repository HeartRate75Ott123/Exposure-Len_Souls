package com.plumejade.lensouls.reinforce;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 超越维度（beyonddimensions）兼容层——纯反射，模组未加载时全部方法直接返回。
 * <p>
 * 该模组的物品不存在于玩家身上（没有玩家 capability / attachment）：
 * <ul>
 *     <li>网络存储：{@code DimensionsNet.getPrimaryNetFromPlayer(player).getUnifiedStorage()}
 *         → {@code IStackHandler.getStorage()} → {@code List<KeyAmount>}（服务端专属，
 *         客户端没有访问入口，因此数量统计与消耗都在服务端完成）；</li>
 *     <li>随身容器：物质压缩球（{@code beyonddimensions:matter_compress_ball}），
 *         内容物存于数据组件 {@code beyonddimensions:istack_slots}（{@code List<KeyAmount>}）。</li>
 * </ul>
 * 一切符号都按已安装 jar（0.7.24）与其**完整源码**核实；取不到时静默降级，不影响其余两个容器。
 * <p>
 * <b>1.5.4 修复「磁铁吸复制之魂进终端 → 之前的复制之魂消失」</b>：
 * {@code sealCopySouls} 的网络迁移原来是「清零旧键 + 覆盖写新键=快照量」，而
 * {@code setAmountByKey} 是<b>绝对量覆盖写</b>。终端网络共享，维度磁铁
 * （{@code NetMagnetItem.workContent} → {@code storage.insert(itemKey, count, false)} 按键合并）
 * / 馈送器 / 其他玩家随时会往两个键里放东西 ⇒ 新键下的<b>已有存量被覆盖成本次迁移量</b>。
 * 现改为合并式迁移：写前 {@code getStackByKey} 重读两键活值，新键写「活值+迁移量」、
 * 旧键扣「实际迁移量」（以 setAmountByKey 返回值核对，容量不足部分下轮幂等收敛）。
 */
public final class BeyondDimensionsCompat {

    private static final String MOD_ID = "beyonddimensions";
    private static final String NET_CLASS = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet";
    private static final String HANDLER_CLASS = "com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler";
    private static final String KEY_AMOUNT_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.KeyAmount";
    private static final String ITEM_KEY_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey";
    private static final String I_STACK_KEY_CLASS = "com.wintercogs.beyonddimensions.api.storage.key.IStackKey";
    private static final String COMPONENTS_CLASS = "com.wintercogs.beyonddimensions.common.init.BDDataComponents";

    private static volatile boolean initialized;
    private static volatile boolean available;
    /** ModList 查询结果缓存（每 tick/每秒都会问一次，别每次都查加载器） */
    private static volatile Boolean loadedCache;

    private static Method getPrimaryNetFromPlayer;
    private static Method getUnifiedStorage;
    private static Method getStorage;
    private static Method extractBySlot;
    /** 可选（封印功能用）：以下任一取不到时封印降级为「只处理随身压缩球」，其余功能不受影响 */
    private static Method setAmountByKey;
    /** 按 key 读当前数量的活值（封印迁移的写前重读用；取不到则网络封印整体降级为不动，绝不覆盖写） */
    private static Method getStackByKey;
    private static Method getAllNetsFromPlayer;
    private static Method getNetPlayers;
    private static Method onChange;
    private static Method keyAccessor;
    private static Method amountAccessor;
    private static Method getReadOnlyStack;
    private static Method hasComponent;
    private static Method getComponent;
    private static Method setComponent;
    private static java.lang.reflect.Constructor<?> keyAmountConstructor;
    private static java.lang.reflect.Constructor<?> itemStackKeyConstructor;

    private BeyondDimensionsCompat() {
    }

    public static boolean isLoaded() {
        Boolean cached = loadedCache;
        if (cached == null) {
            cached = ModList.get().isLoaded(MOD_ID);
            loadedCache = cached;
        }
        return cached;
    }

    /**
     * 汇总超越维度的物品数量到 counts（网络存储 + 随身物质压缩球）。
     * <p>
     * 只统计 {@code wanted}（界面关心的那几种材料），并在全部材料都出现过之后立刻停止扫描——
     * 网络存储上万条时这是最有效的省时手段（实测 5 万条：0.98 ms → 0.07 ms）。
     */
    public static void tally(Player player, Map<Item, Long> counts, List<ItemStack> carried, Set<Item> wanted) {
        if (!ensureReady() || wanted == null || wanted.isEmpty()) return;
        Object unified = unifiedStorage(player);
        if (unified != null) {
            Object storage = invoke(getStorage, unified);
            if (storage instanceof List<?> list) {
                for (Object entry : list) {
                    if (addKeyAmount(entry, counts, wanted) && counts.size() >= wanted.size()) return;
                }
            }
        }
        for (ItemStack stack : carried) {
            for (Object entry : componentEntries(stack)) {
                if (addKeyAmount(entry, counts, wanted) && counts.size() >= wanted.size()) return;
            }
        }
    }

    /** 从超越维度消耗 1 个物品：先网络存储，再随身物质压缩球。 */
    public static boolean consume(Player player, Item item, List<ItemStack> carried) {
        if (!ensureReady()) return false;
        Object unified = unifiedStorage(player);
        if (unified != null) {
            Object storage = invoke(getStorage, unified);
            if (storage instanceof List<?> list) {
                int slot = 0;
                for (Object entry : list) {
                    ItemStack stored = stackOf(entry);
                    if (stored != null && stored.is(item)) {
                        Object extracted = invoke(extractBySlot, unified, slot, 1L, false);
                        if (amountOf(extracted) > 0) return true;
                    }
                    slot++;
                }
            }
        }
        for (ItemStack stack : carried) {
            if (consumeFromComponent(stack, item)) return true;
        }
        return false;
    }

    // ========== 内部实现 ==========

    /**
     * 给超越维度里的复制之魂打 / 摘「禁复制封印」。
     * <p>
     * 三个必须踩对的点（全部对着实际安装的 0.7.24 jar 核实过）：
     * <ol>
     *   <li>{@code getStorage()} 是<b>只读活视图</b>（{@code Collections.unmodifiableList} +
     *       每次 {@code get(i)} 现造 {@code KeyAmount}），而 {@code KeyAmount} 是不可变 record
     *       ——所以只能<b>先快照、再按键写回</b>；</li>
     *   <li>写回<b>必须按键</b>（{@code setAmountByKey}）：底层 {@code slotIndex} 用的是
     *       「换尾删除」，按 index 连续改写会漏掉被搬到当前位置的那条；</li>
     *   <li>{@code getReadOnlyStack()} 返回的是<b>共享缓存实例</b>（数量被强制置 1），
     *       改它不会改存档、还会污染后续所有读取——要改必须先 {@code copy()}，
     *       再用 {@code new ItemStackKey(改好的副本)} 换 key（改组件 = 换 key）。</li>
     * </ol>
     * 解封方向额外一条：终端网络是<b>共享</b>的（同一网络的多个玩家看到同一份）。只要该网络
     * 还有在线成员戴着禁复制羽毛，就不能把魂解封（否则戴羽毛的玩家顺手就能复制）。
     *
     * @return 是否真的改动了任何条目
     */
    public static boolean sealCopySouls(Player player, boolean seal, List<ItemStack> carried) {
        if (!ensureReady()) return false;
        boolean changed = false;

        // 1) 网络存储（可能同时属于多个网络）
        for (Object net : allNets(player)) {
            if (net == null) continue;
            if (!seal && netHasFeatherMember(player, net)) continue; // 共享网络：有人还戴着就不解封
            Object unified = invoke(getUnifiedStorage, net);
            if (unified == null) continue;
            if (getStackByKey == null) {
                // 缺"按 key 读活值"句柄：合并式迁移做不了，宁可不动也绝不覆盖写（覆盖写=丢存量）
                LenSouls.LOGGER.debug(
                        "[CopySoulSeal] 缺 getStackByKey 句柄，网络封印降级为不动（避免覆盖写丢存量）");
                continue;
            }
            Object listed = invoke(getStorage, unified);
            if (!(listed instanceof List<?> live)) continue;

            // 活视图：必须先整体快照，遍历中改存储不会抛 CME，只会静默错乱
            List<Object> snapshot = new ArrayList<>(live);
            boolean netChanged = false;
            for (Object entry : snapshot) {
                if (entry == null) continue;
                Object oldKey = keyOf(entry);
                if (oldKey == null) continue;
                ItemStack updated = reseal(stackOf(entry), seal);
                if (updated == null) continue;
                Object newKey = newItemStackKey(updated);
                if (newKey == null) continue;

                // ── 写前重读活值（快照只是一份"计划"，数量以此刻的存储为准）──
                // 终端网络是共享的：维度磁铁吸掉落物 / 馈送器 / 其他玩家随时可能往这两个键合并。
                // setAmountByKey 是绝对量覆盖写，照抄快照量会把新键下的存量覆盖掉（磁铁丢魂根因）。
                long oldLive = amountOf(invoke(getStackByKey, unified, oldKey));
                if (oldLive <= 0L) continue; // 旧键已被别人清空/搬走：本轮不动，下轮按新快照收敛
                long moved = Math.min(amountOf(entry), oldLive);

                long newLive = amountOf(invoke(getStackByKey, unified, newKey));

                // 先加新键（合并），返回值 = 实际写入量（新键槽容量不足时会被钳制）；
                // 反射失败/异常 → 0 → actual 为负 → 跳过本轮（绝不盲目覆盖写）
                long applied = amountOf(invoke(setAmountByKey, unified, newKey, newLive + moved));
                long actual = applied - newLive;
                if (actual <= 0L) continue; // 容量已满：本轮不动，下轮再试

                // 按实际迁移量扣旧键（可能剩一小部分，下一轮 sweep 幂等收敛）
                invoke(setAmountByKey, unified, oldKey, oldLive - actual);
                netChanged = true;
            }
            if (netChanged) {
                invoke(onChange, unified);
                changed = true;
            }
        }

        // 2) 随身物质压缩球（数据组件 istack_slots，组件直接写在活 ItemStack 上即可持久化）
        for (ItemStack holder : carried) {
            List<Object> entries = componentEntries(holder);
            if (entries.isEmpty()) continue;
            List<Object> replaced = new ArrayList<>(entries.size());
            boolean localChanged = false;
            for (Object entry : entries) {
                ItemStack updated = reseal(stackOf(entry), seal);
                Object newEntry = null;
                if (updated != null) {
                    Object key = newItemStackKey(updated);
                    if (key != null) newEntry = newKeyAmount(key, amountOf(entry));
                }
                if (newEntry == null) {
                    replaced.add(entry);
                    continue;
                }
                replaced.add(newEntry);
                localChanged = true;
            }
            if (localChanged) {
                invoke(setComponent, holder, replaced);
                changed = true;
            }
        }
        return changed;
    }

    /** 玩家所属的全部维度网络；取不到 {@code getAllNetFromPlayer} 时退回主网络。 */
    private static List<Object> allNets(Player player) {
        List<Object> nets = new ArrayList<>(2);
        if (getAllNetsFromPlayer != null) {
            Object value = invoke(getAllNetsFromPlayer, null, player);
            if (value instanceof List<?> list) {
                nets.addAll(list);
                return nets;
            }
        }
        Object primary = invoke(getPrimaryNetFromPlayer, null, player);
        if (primary != null) nets.add(primary);
        return nets;
    }

    /** 该网络是否还有在线成员戴着禁复制羽毛（戴着则禁止解封）。 */
    private static boolean netHasFeatherMember(Player player, Object net) {
        if (getNetPlayers == null) return false;
        Object members = invoke(getNetPlayers, net);
        if (!(members instanceof java.util.Set<?> set)) return false;
        var server = player.getServer();
        if (server == null) return false;
        var playerList = server.getPlayerList();
        if (playerList == null) return false;
        for (Object id : set) {
            if (!(id instanceof java.util.UUID uuid)) continue;
            var online = playerList.getPlayer(uuid);
            if (online != null && com.plumejade.lensouls.handler.CopySoulSealHandler.wearsForbiddenFeather(online)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 需要改封印时返回「改好的副本」，不需要改返回 null。
     * 只处理本模组的复制之魂；其它物品连组件都不碰。
     * {@code stored} 可能是共享缓存实例，因此一律 copy 后再改。
     */
    private static ItemStack reseal(ItemStack stored, boolean seal) {
        if (stored == null || stored.isEmpty()) return null;
        if (stored.getItem() != com.plumejade.lensouls.item.ModItems.COPY_SOUL.get()) return null;
        boolean sealed = stored.has(com.plumejade.lensouls.component.ModDataComponents.COPY_SOUL_SEALED.get());
        if (sealed == seal) return null;
        ItemStack copy = stored.copy();
        if (seal) {
            copy.set(com.plumejade.lensouls.component.ModDataComponents.COPY_SOUL_SEALED.get(), true);
        } else {
            copy.remove(com.plumejade.lensouls.component.ModDataComponents.COPY_SOUL_SEALED.get());
        }
        return copy;
    }

    private static Object newItemStackKey(ItemStack stack) {
        if (itemStackKeyConstructor == null) return null;
        try {
            return itemStackKeyConstructor.newInstance(stack);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** 读取物质压缩球组件里的条目；其它物品返回空列表。 */
    private static List<Object> componentEntries(ItemStack stack) {
        if (stack.isEmpty() || hasComponent == null) return List.of();
        Object present = invoke(hasComponent, stack);
        if (!(present instanceof Boolean flag) || !flag) return List.of();
        Object value = invoke(getComponent, stack);
        if (value instanceof List<?> list) return new ArrayList<>(list);
        return List.of();
    }

    /**
     * 把一条 KeyAmount 计入 counts（仅当它是 wanted 里的物品）。
     *
     * @return 本次是否真的计入了一个材料（供上层判断是否可以提前退出）
     */
    private static boolean addKeyAmount(Object entry, Map<Item, Long> counts, Set<Item> wanted) {
        if (entry == null) return false;
        ItemStack stored = stackOf(entry);
        if (stored == null || stored.isEmpty()) return false;
        Item item = stored.getItem();
        if (!wanted.contains(item)) return false;
        long amount = amountOf(entry);
        if (amount <= 0) return false;
        counts.merge(item, amount, Long::sum);
        return true;
    }

    private static boolean consumeFromComponent(ItemStack holder, Item item) {
        List<Object> entries = componentEntries(holder);
        if (entries.isEmpty()) return false;
        List<Object> replaced = new ArrayList<>(entries.size());
        boolean consumed = false;
        for (Object entry : entries) {
            ItemStack stored = stackOf(entry);
            long amount = amountOf(entry);
            if (!consumed && stored != null && stored.is(item) && amount > 0) {
                consumed = true;
                long left = amount - 1;
                if (left > 0) replaced.add(newKeyAmount(keyOf(entry), left));
                continue;
            }
            replaced.add(entry);
        }
        if (!consumed) return false;
        invoke(setComponent, holder, replaced);
        return true;
    }

    private static Object newKeyAmount(Object key, long amount) {
        if (keyAmountConstructor == null) return null;
        try {
            return keyAmountConstructor.newInstance(key, amount);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static Object unifiedStorage(Player player) {
        Object net = invoke(getPrimaryNetFromPlayer, null, player);
        if (net == null) return null;
        return invoke(getUnifiedStorage, net);
    }

    private static ItemStack stackOf(Object keyAmount) {
        Object key = keyOf(keyAmount);
        if (key == null || getReadOnlyStack == null) return null;
        if (!key.getClass().getName().equals(ITEM_KEY_CLASS)) return null;
        Object stack = invoke(getReadOnlyStack, key);
        return stack instanceof ItemStack itemStack ? itemStack : null;
    }

    private static Object keyOf(Object keyAmount) {
        return keyAmount == null ? null : invoke(keyAccessor, keyAmount);
    }

    private static long amountOf(Object keyAmount) {
        Object amount = keyAmount == null ? null : invoke(amountAccessor, keyAmount);
        return amount instanceof Long value ? value : 0L;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean ensureReady() {
        if (initialized) return available;
        synchronized (BeyondDimensionsCompat.class) {
            if (initialized) return available;
            initialized = true;
            try {
                if (!isLoaded()) {
                    available = false;
                    return false;
                }
                Class<?> netClass = Class.forName(NET_CLASS);
                Class<?> handlerClass = Class.forName(HANDLER_CLASS);
                Class<?> keyAmountClass = Class.forName(KEY_AMOUNT_CLASS);
                Class<?> itemKeyClass = Class.forName(ITEM_KEY_CLASS);
                Class<?> stackKeyClass = Class.forName(I_STACK_KEY_CLASS);
                Class<?> componentsClass = Class.forName(COMPONENTS_CLASS);
                Class<?> componentTypeClass = Class.forName("net.minecraft.core.component.DataComponentType");

                getPrimaryNetFromPlayer = netClass.getMethod("getPrimaryNetFromPlayer", Player.class);
                getUnifiedStorage = netClass.getMethod("getUnifiedStorage");
                getStorage = handlerClass.getMethod("getStorage");
                extractBySlot = handlerClass.getMethod("extract", int.class, long.class, boolean.class);
                keyAccessor = keyAmountClass.getMethod("key");
                amountAccessor = keyAmountClass.getMethod("amount");
                getReadOnlyStack = itemKeyClass.getMethod("getReadOnlyStack");
                keyAmountConstructor = keyAmountClass.getConstructor(stackKeyClass, long.class);

                // 封印功能专用的句柄：单独 try，缺任何一个都不影响上面的统计/消耗
                try {
                    Class<?> abstractHandlerClass = Class.forName(
                            "com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler");
                    setAmountByKey = abstractHandlerClass.getMethod("setAmountByKey", stackKeyClass, long.class);
                    getStackByKey = handlerClass.getMethod("getStackByKey", stackKeyClass);
                    onChange = handlerClass.getMethod("onChange");
                    itemStackKeyConstructor = itemKeyClass.getConstructor(net.minecraft.world.item.ItemStack.class);
                    getAllNetsFromPlayer = netClass.getMethod("getAllNetFromPlayer", Player.class);
                    getNetPlayers = netClass.getMethod("getPlayers");
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // 旧版本缺符号：封印只覆盖随身压缩球
                }

                Object holder = componentsClass.getField("ISTACK_SLOTS").get(null);
                Object type = holder.getClass().getMethod("get").invoke(holder);
                hasComponent = net.minecraft.world.item.ItemStack.class
                        .getMethod("has", componentTypeClass);
                getComponent = net.minecraft.world.item.ItemStack.class
                        .getMethod("get", componentTypeClass);
                setComponent = net.minecraft.world.item.ItemStack.class
                        .getMethod("set", componentTypeClass, Object.class);
                if (type == null) {
                    available = false;
                    return false;
                }
                available = true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                available = false;
            }
            return available;
        }
    }
}
