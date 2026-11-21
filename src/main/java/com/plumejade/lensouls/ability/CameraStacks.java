package com.plumejade.lensouls.ability;

import com.plumejade.lensouls.ability.camera.CameraInCurio;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 相机栈的「双端」查找工具。
 * <p>
 * ⚠ 这两个方法以前放在 {@code handler.CameraInputHandler} 里，而那个类引用
 * {@code LocalPlayer} / {@code Minecraft} / {@code KeyMapping}，属于仅客户端类。
 * 专用服务器上第一次被服务端逻辑调用（打开能力界面、滚轮切能力、拍照缓存能力）
 * 就会触发 {@code RuntimeDistCleaner} 抛
 * {@code Attempted to load class ... LocalPlayer for invalid dist DEDICATED_SERVER}，
 * 于是界面打不开、选择写不进去、能力照片永远不产生。
 * <p>
 * 因此凡是**服务端也要用**的相机查找一律走本类；{@code CameraInputHandler} 只管客户端输入。
 */
public final class CameraStacks {

    private static final ResourceLocation CAMERA_ID = ResourceLocation.parse("exposure:camera");
    private static final ResourceLocation POLAROID_ID = ResourceLocation.parse("exposure_polaroid:instant_camera");

    private CameraStacks() {
    }

    /** 相机检测（按 ID 比对，无编译依赖）；双端可用。 */
    public static boolean isCamera(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return CAMERA_ID.equals(id) || POLAROID_ID.equals(id);
    }

    /**
     * 返回玩家当前手持相机（主手优先，其次副手）；无则空栈。
     * <p>
     * 饰品栏相机正在取景时视同「手上这台」——能力 HUD/轮盘/菜单/服务端校验全都靠这个方法定位相机，
     * 不补这条就会表现为「从饰品栏打开取景后当前能力丢失」。
     */
    public static ItemStack getWieldedCamera(Player player) {
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        if (isCamera(main)) return main;
        ItemStack off = player.getOffhandItem();
        if (isCamera(off)) return off;
        if (player instanceof io.github.mortuusars.exposure.world.entity.CameraOperator operator) {
            if (operator.getActiveExposureCamera() instanceof CameraInCurio curio) {
                ItemStack curioStack = curio.getItemStack();
                if (isCamera(curioStack)) return curioStack;
            }
        }
        return ItemStack.EMPTY;
    }
}
