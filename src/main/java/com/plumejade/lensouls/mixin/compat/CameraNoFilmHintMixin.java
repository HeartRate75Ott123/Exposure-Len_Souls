package com.plumejade.lensouls.mixin.compat;

import com.plumejade.lensouls.client.feather.FeatherToast;
import io.github.mortuusars.exposure.world.entity.CameraHolder;
import io.github.mortuusars.exposure.world.item.FilmRollItem;
import io.github.mortuusars.exposure.world.item.camera.Attachment;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「胶卷用光」提示：快门按下去但拍不出来时，用最上层弹窗告诉玩家原因。
 * <p>
 * Exposure 自己的反馈只有取景器里那个小 {@code no_film} 图标（外加一声释放音），
 * 在取景器外、或者压根没注意到右上角图标时，玩家只会觉得"按了没反应"。
 * <p>
 * 挂 {@code CameraItem.release}（快门释放）的 HEAD —— 这就是"按了快门"的唯一入口：
 * 它先 {@code playSided} 播一声释放音，然后 {@code !canTakePhoto(...)} 就静默返回。
 * 这里只判断<b>胶片这一项</b>（能不能再放一张），冷却/定时器/快门开着都不算"胶卷用光"，
 * 不抢其它原因的提示。客户端与服务端都会走 release，所以必须 {@code LocalPlayer} 守卫。
 * <p>
 * <b>只对胶卷相机生效</b>：拍立得（{@code exposure_polaroid:instant_camera}）用的是自己的相纸，
 * 没有胶卷这个概念 —— 它的 {@code Attachment.FILM} 取不到内容，于是会被误判成"相机里还没装胶卷"，
 * 出现"拍立得还弹胶卷用光"的诡异提示（用户实测）。所以先做一次"是不是胶卷相机"的判断。
 */
@Mixin(value = CameraItem.class, remap = false)
public class CameraNoFilmHintMixin {

    /** 同一句提示的最小间隔（毫秒）：按住右键时会反复 release，避免刷屏 */
    private static final long HINT_COOLDOWN_MS = 800L;

    private static long lensouls$lastHint;

    @Inject(method = "release", at = @At("HEAD"), require = 0, remap = false)
    private void lensouls$noFilmHint(CameraHolder holder, ItemStack stack,
                                     CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!(holder.asHolderEntity() instanceof LocalPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        // 拍立得 / 相纸相机不参与"胶卷"这套提示
        if (!lensouls$isFilmCamera(stack)) return;

        // 胶片还能再放一张 → 不是"用光"，交给 Exposure 自己拍
        if (Attachment.FILM.map(stack, FilmRollItem::canAddFrame).orElse(false)) return;

        ItemStack film = Attachment.FILM.get(stack).getForReading();
        Component hint = film.isEmpty()
                ? Component.translatable("message.lensouls.no_film")
                : Component.translatable("message.lensouls.film_used_up");

        long now = System.currentTimeMillis();
        if (now - lensouls$lastHint < HINT_COOLDOWN_MS) return;
        lensouls$lastHint = now;
        FeatherToast.show(hint);
    }

    /**
     * 是不是"用胶卷的相机"。
     * <p>
     * 判据取两条，任一命中就不算：① 物品 ID 是 {@code exposure_polaroid:instant_camera}；
     * ② 物品类在 {@code exposure_polaroid} 包里（对方以后改名/加型号也覆盖）。
     * 用类名而不是硬依赖：拍立得装在 run/mods 里是 implementation，但这样写不会因为
     * 对方包结构变动就编译失败。
     */
    private static boolean lensouls$isFilmCamera(ItemStack stack) {
        try {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null && "exposure_polaroid".equals(id.getNamespace())) return false;
            String cls = stack.getItem().getClass().getName();
            return !cls.startsWith("io.github.mortuusars.exposure_polaroid");
        } catch (Throwable ignored) {
            return true;
        }
    }
}
