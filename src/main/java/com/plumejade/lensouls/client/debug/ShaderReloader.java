package com.plumejade.lensouls.client.debug;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.plumejade.lensouls.LenSouls;
import com.plumejade.lensouls.ability.client.BossOutlineManager;
import com.plumejade.lensouls.ability.client.FrozenOutlineManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 开发专用着色器热重载工具。
 * <p>
 * 在 {@code RegisterShadersEvent} 中注册的着色器只在启动时编译一次。
 * 修改 {@code .fsh} / {@code .vsh} 文件后，调用 {@link #reloadAll()}
 * 可运行时重新编译所有 CoreShader，无需重启游戏。
 * <p>
 * 只在 dev 环境可用（{@link FMLLoader#isProduction()} == false）。
 */
public class ShaderReloader {

    private static final Logger LOGGER = LoggerFactory.getLogger("ShaderReloader");

    private ShaderReloader() {}

    /** 是否生产环境（生产环境禁用此工具） */
    public static boolean isProduction() {
        return FMLLoader.isProduction();
    }

    /**
     * 重载所有镜魂描边相关的 CoreShader。
     * <p>
     * 从 {@link ResourceProvider} 重新读取 .json/.vsh/.fsh → 编译链接 → 替换静态引用。
     * 旧着色器的 GL 程序被 {@link ShaderInstance#close()} 释放。
     */
    public static void reloadAll() {
        if (isProduction()) {
            LOGGER.warn("[ShaderReloader] 生产环境已禁用");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            LOGGER.warn("[ShaderReloader] Minecraft 实例不可用");
            return;
        }

        ResourceProvider provider = mc.getResourceManager();
        long start = Util.getNanos();

        int ok = 0, fail = 0;

        // ── 金边复合着色器（Shared Sobel） ──
        if (reloadOne(provider, "rendertype_gold_outline", DefaultVertexFormat.POSITION_TEX,
                instance -> FrozenOutlineManager.goldOutlineShader = instance)) {
            ok++;
        } else {
            fail++;
        }

        // ── 实体蒙版（纯白，NEW_ENTITY 格式） ──
        if (reloadOne(provider, "rendertype_mask_entity", DefaultVertexFormat.NEW_ENTITY,
                instance -> FrozenOutlineManager.maskShader = instance)) {
            ok++;
        } else {
            fail++;
        }

        // ── 物品蒙版（alpha test，NEW_ENTITY 格式） ──
        if (reloadOne(provider, "rendertype_soul_glow_mask_item", DefaultVertexFormat.NEW_ENTITY,
                instance -> FrozenOutlineManager.itemMaskShader = instance)) {
            ok++;
        } else {
            fail++;
        }

        // ── BOSS 镜魂描边 composite（第一人称手部 mask） ──
        if (reloadOne(provider, "boss_outline_composite", DefaultVertexFormat.POSITION_TEX,
                instance -> BossOutlineManager.bossCompositeShader = instance)) {
            ok++;
        } else {
            fail++;
        }

        long elapsed = (Util.getNanos() - start) / 1_000_000L;

        if (fail > 0) {
            LOGGER.warn("[ShaderReloader] {} 个着色器重载失败，检查日志确认具体错误", fail);
        }
    }

    /**
     * 重载单个着色器：关闭旧实例 → 编译新实例 → 赋值。
     *
     * @return true 成功，false 失败
     */
    private static boolean reloadOne(ResourceProvider provider,
                                     String shaderName,
                                     VertexFormat format,
                                     java.util.function.Consumer<ShaderInstance> setter) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, shaderName);
        try {
            // 先关闭旧的 GL 程序（释放 GPU 资源）
            ShaderInstance old = getCurrentInstance(shaderName);
            if (old != null) {
                old.close();
            }

            // 编译新着色器
            ShaderInstance fresh = new ShaderInstance(provider, loc, format);
            setter.accept(fresh);
            return true;
        } catch (IOException e) {
            LOGGER.error("[ShaderReloader]  ✗ {}: {}", shaderName, e.getMessage());
            return false;
        }
    }

    /** 获取当前着色器实例引用（用于旧实例清理） */
    private static ShaderInstance getCurrentInstance(String name) {
        return switch (name) {
            case "rendertype_gold_outline"     -> FrozenOutlineManager.goldOutlineShader;
            case "rendertype_mask_entity"      -> FrozenOutlineManager.maskShader;
            case "rendertype_soul_glow_mask_item" -> FrozenOutlineManager.itemMaskShader;
            case "boss_outline_composite"      -> BossOutlineManager.bossCompositeShader;
            default -> null;
        };
    }
}
