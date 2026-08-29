package com.plumejade.lensouls.client;

import com.plumejade.lensouls.entity.BossPhantomType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 临时内存泄漏诊断（仅 -Dlensouls.leakdiag=true 时启用，不进发布）。
 * <p>
 * 娜迦镜魂可复现原生 OOM：在幻灵激活期间周期采集 Sodium 原生缓冲总量、
 * JVM 堆/提交内存、实体与加载区块数，以定位泄漏点。Sodium 内存追踪开启后，
 * 泄漏的原生缓冲被回收时会打印分配点栈。
 */
public class PhantomMemoryLeakDiagnostic {
    private static final boolean ENABLED = Boolean.getBoolean("lensouls.leakdiag");
    private static final Logger LOGGER = LoggerFactory.getLogger("LensoulsLeakDiag");

    private static boolean sodiumReady = false;
    private static Method sodiumGetTotalAllocated;
    private static Method sodiumReclaim;

    private static boolean running = false;
    private static BossPhantomType currentType;
    private static int tickCounter = 0;
    /** 无虚灵空闲心跳计数（每 ~5s 采样一次 committedVM，做无虚灵基线对照）。 */
    private static int idleTickCounter = 0;

    public static boolean enabled() {
        return ENABLED;
    }

    public static void onPhantomStart(BossPhantomType type) {
        if (!ENABLED) return;
        currentType = type;
        running = true;
        tickCounter = 0;
        setupSodium();
        LOGGER.info("[LeakDiag] phantom start type={}, sodiumReady={}", type, sodiumReady);
        sample("start");
    }

    public static void onPhantomStop() {
        if (!ENABLED || !running) return;
        running = false;
        sample("stop");
    }

    public static void clientTick() {
        if (!ENABLED) return;
        if (running) {
            tickCounter++;
            if (tickCounter % 40 == 0) {
                sample("tick+" + tickCounter);
            }
        } else {
            // 空闲心跳：无虚灵时也周期采样，用于判断泄漏是否与虚灵相关
            idleTickCounter++;
            if (idleTickCounter % 100 == 0) {
                sample("idle");
            }
        }
    }

    private static void setupSodium() {
        if (sodiumReady) return;
        try {
            Class<?> nb = Class.forName("net.caffeinemc.mods.sodium.client.util.NativeBuffer");
            sodiumGetTotalAllocated = nb.getMethod("getTotalAllocated");
            try {
                sodiumReclaim = nb.getMethod("reclaim", boolean.class);
            } catch (NoSuchMethodException ignore) {
                sodiumReclaim = null;
            }

            Class<?> mod = Class.forName("net.caffeinemc.mods.sodium.client.SodiumClientMod");
            Object options = mod.getMethod("options").invoke(null);
            Object advanced = null;
            try {
                advanced = options.getClass().getMethod("advanced").invoke(options);
            } catch (ReflectiveOperationException e) {
                try {
                    advanced = options.getClass().getField("advanced").get(options);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            if (advanced != null) {
                for (String name : new String[]{"enableMemoryTracing", "memoryTracing", "trackMemoryAllocations"}) {
                    try {
                        Field f = advanced.getClass().getField(name);
                        f.setBoolean(advanced, true);
                        sodiumReady = true;
                        LOGGER.info("[LeakDiag] Sodium memory tracing enabled via field '{}'", name);
                        return;
                    } catch (ReflectiveOperationException ignored) {
                        try {
                            Field f = advanced.getClass().getDeclaredField(name);
                            f.setAccessible(true);
                            f.setBoolean(advanced, true);
                            sodiumReady = true;
                            LOGGER.info("[LeakDiag] Sodium memory tracing enabled via declared field '{}'", name);
                            return;
                        } catch (ReflectiveOperationException ignore2) {
                        }
                    }
                }
            }
            LOGGER.warn("[LeakDiag] Sodium advanced options not found; native probe disabled");
        } catch (Throwable t) {
            LOGGER.warn("[LeakDiag] Sodium reflection failed: {}", t);
        }
    }

    @SuppressWarnings("unused")
    private static void sample(String tag) {
        try {
            MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
            long heapUsed = mem.getHeapMemoryUsage().getUsed();
            long heapMax = mem.getHeapMemoryUsage().getMax();

            long nativeBytes = -1L;
            if (sodiumReady && sodiumGetTotalAllocated != null) {
                nativeBytes = (long) sodiumGetTotalAllocated.invoke(null);
            }

            long committed = -1L;
            long freePhys = -1L;
            long totalPhys = -1L;
            try {
                com.sun.management.OperatingSystemMXBean os =
                        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                committed = os.getCommittedVirtualMemorySize();
                freePhys = os.getFreePhysicalMemorySize();
                totalPhys = os.getTotalPhysicalMemorySize();
            } catch (Throwable ignore) {
            }

            int chunks = -1;
            Minecraft mc = Minecraft.getInstance();
            Level level = mc.level;
            if (level != null) {
                try {
                    Object cs = level.getChunkSource();
                    chunks = (int) cs.getClass().getMethod("getLoadedChunksCount").invoke(cs);
                } catch (Throwable ignore) {
                }
            }

            LOGGER.info("[LeakDiag][{}] type={} sodiumNative={} heap={}/{} committedVM={} freeRAM={}/{} chunks={}",
                    tag, currentType, nativeBytes, heapUsed, heapMax, committed, freePhys, totalPhys, chunks);
        } catch (Throwable t) {
            LOGGER.warn("[LeakDiag] sample failed: {}", t.getMessage());
        }
    }
}
