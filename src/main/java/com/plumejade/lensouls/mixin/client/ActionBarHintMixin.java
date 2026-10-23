package com.plumejade.lensouls.mixin.client;

import com.plumejade.lensouls.client.hud.ViewfinderOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管动作栏消息，好在"原版画不出来"的场合改由我们自己画在最上层。
 * <p>
 * 典型场景：相机取景器一开，Exposure 会 cancel 掉整个 {@code Gui.render}，于是
 * {@code message.lensouls.hardman.inject_fail}（"奇怪，这东西怎么用来着"）这类提示压根不会显示。
 * 这里只做"登记"，是否真的接管由 {@link ViewfinderOverlay#captureActionBar} 决定
 * （有屏幕打开 / 取景器藏 HUD 才接管，其余仍走原版动作栏，避免同一句出现两份）。
 */
@Mixin(ClientPacketListener.class)
public class ActionBarHintMixin {

    @Inject(method = "setActionBarText", at = @At("TAIL"), require = 1)
    private void lensouls$captureActionBarHint(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        ViewfinderOverlay.captureActionBar(packet.text());
    }
}
