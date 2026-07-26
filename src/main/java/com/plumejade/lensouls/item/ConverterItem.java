package com.plumejade.lensouls.item;

import com.plumejade.lensouls.gui.ConverterMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 转换器——9 槽镜魂容器。
 * <p>
 * 右键打开 GUI 放入镜魂，背包内通过快捷键激活第一个可用的镜魂。
 */
public class ConverterItem extends Item {

    public ConverterItem(Properties properties) {
        super(properties);
    }

    @Override
    @NotNull
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }

        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new ConverterMenu(id, inv, p.getItemInHand(hand)),
                Component.translatable("container.lensouls.converter")
        ));

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String keyName = getConverterKeyName();
        tooltip.add(Component.translatable("item.lensouls.converter.tooltip", keyName)
                .withStyle(ChatFormatting.GRAY));
    }

    /** 获取转换器快捷键的当前键名（客户端动态读取，服务端回退到 "G"） */
    private static String getConverterKeyName() {
        try {
            // 反射加载 KeyBindings 避免服务端直接引用 client-only 类
            Class<?> kbClass = Class.forName("com.plumejade.lensouls.key.KeyBindings");
            var field = kbClass.getDeclaredField("CONVERTER_KEY");
            field.setAccessible(true);
            Object lazy = field.get(null);
            // Lazy.get() → KeyMapping → getTranslatedKeyMessage() → Component
            var getMethod = lazy.getClass().getMethod("get");
            Object mapping = getMethod.invoke(lazy);
            if (mapping != null) {
                var gtmMethod = mapping.getClass().getMethod("getTranslatedKeyMessage");
                var component = (net.minecraft.network.chat.Component) gtmMethod.invoke(mapping);
                return component.getString();
            }
        } catch (Exception | NoClassDefFoundError ignored) {}
        return "G";
    }
}
