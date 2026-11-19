package com.plumejade.lensouls.ability.network;

import com.plumejade.lensouls.LenSouls;
import io.github.mortuusars.exposure.world.camera.Camera;
import io.github.mortuusars.exposure.world.entity.CameraOperator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * C2S：饰品栏相机按下快门。
 * <p>
 * 原版 Exposure 的右键释放只认两种相机：手上的（走 {@code CameraItem.use}）与相机架上的
 * （{@code MinecraftMixin.onStartUseItem} 里只判 {@code CameraOnStand}）。饰品栏相机两者都不是，
 * 客户端按右键不会发任何东西 ⇒ 永远拍不了。这里补一个专用包，让服务端对当前活跃相机调
 * {@link Camera#release()}，与手持完全同一条链路（冷却、消耗、出片都在里面）。
 */
public class CameraShootPacket implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CameraShootPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, "camera_shoot"));

    public static final CameraShootPacket INSTANCE = new CameraShootPacket();

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShootPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    @NotNull
    public CustomPacketPayload.Type<CameraShootPacket> type() {
        return TYPE;
    }

    public static void handle(CameraShootPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Camera active = ((CameraOperator) sp).getActiveExposureCamera();
            if (active == null) return;
            if (active.isEmpty()) return;
            // 饰品栏相机拿不到原版「手持右键」那条链路（PlayerInteractEvent.RightClickItem），
            // 于是两个「右键即生效」的能力在快捷取景框里只剩特效、按快门什么都不会发生。
            // 这里在 release 之前补同一条判定：被能力接管了就不消耗相纸、也不出片。
            if (active instanceof com.plumejade.lensouls.ability.camera.CameraInCurio) {
                net.minecraft.world.item.ItemStack stack = active.getItemStack();
                // 冷却自己先拦一道：手持相机时「物品冷却」会在原版 startUseItem 阶段直接拦掉右键，
                // 连 RightClickItem 都不会派发；饰品相机这条 C2S 路径绕过了那层，于是冷却中照样能
                // 触发要害打击/夺魂索命（它们把冷却加在相机上，却没人看）。冷却本身是 player×Item
                // 的 ItemCooldowns，与物品在不在手上无关，直接按活跃相机那根栈问。
                if (stack.getItem() instanceof io.github.mortuusars.exposure.world.item.camera.CameraItem cameraItem
                        && cameraItem.isOnCooldown(
                                (io.github.mortuusars.exposure.world.entity.CameraHolder) sp, stack)) {
                    com.plumejade.lensouls.LenSouls.LOGGER.debug("[Camera] 饰品相机冷却中，快门被拦下");
                    return;
                }
                if (com.plumejade.lensouls.ability.handler.VitalStrikeHandler.tryCurioShot(sp, stack)) return;
                if (com.plumejade.lensouls.ability.handler.SoulSeverHandler.tryCurioShot(sp, stack)) return;
            }
            active.release();
        });
    }
}