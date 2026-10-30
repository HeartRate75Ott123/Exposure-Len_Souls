package com.plumejade.lensouls.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 机械眼 —— <b>Exposure 相机配件</b>，装在相机的<b>闪光灯槽</b>（原版右上角放红石灯的那个位置）。
 * <p>
 * 注册进 {@code data/exposure/tags/item/flashes.json} 即可安装：Exposure 判定闪光槽的谓词是
 * {@code Attachment.FLASH} 的 {@code stack.is(Exposure.Tags.Items.FLASHES)}，只认标签，不需要额外接口或 mixin。
 * <p>
 * <b>它只改一处视觉</b>：白霸体挡下削韧（且本次没有任何一只真吃到削韧）时，默认弹的是屏幕上方
 * 那个 18px 的原版虚弱小图标；装了这个配件，同一处改弹原本的「赛博朋克故障叉号」
 * （见 {@code client/hud/HyperArmorDenyIcon} 与 {@code client/hud/VitalDenyOverlay}）。
 * 触发口径不变，静默能力（能力窃取/弱点透镜/空间扭曲/时空回溯）仍然不给大叉号 —— 只给小平图标。
 * <p>
 * tooltip 按用户要求「效果含糊描述」：只暗示会改写那一下提示，不写死"变成故障叉号"。
 */
public class MechanicalEyeItem extends Item {

    public MechanicalEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.lensouls.mechanical_eye.intro"));
        tooltip.add(Component.translatable("tooltip.lensouls.mechanical_eye.effect"));
    }
}
