package com.plumejade.lensouls.event;

import com.plumejade.lensouls.Config;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.BasicItemListing;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

/**
 * 村民交易处理器。
 * <p>
 * 大师级图书管理员有几率出售摄魂术附魔书。
 * 通过 {@link Config#ENABLE_ENCHANTMENT_LOOT} 控制开关。
 */
public class VillagerTradeHandler {

    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.LIBRARIAN) return;
        // 配置开关：关闭时跳过村民交易
        if (!Config.ENABLE_ENCHANTMENT_LOOT.get()) return;

        // 获取摄魂术附魔持有者
        var registry = event.getRegistryAccess();
        var holder = registry.registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(ModEnchantments.SOUL_PHOTOGRAPHY_KEY);

        // 创建附魔书
        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchantedBook.enchant(holder, 1);

        // 大师级（5级）交易：30绿宝石 + 1本书 → 摄魂术附魔书
        event.getTrades().get(5).add(new BasicItemListing(
                new ItemStack(Items.EMERALD, 30),  // 绿宝石价格
                new ItemStack(Items.BOOK),          // 附加输入：一本书
                enchantedBook,                      // 出售物品
                3,                                  // 最大交易次数
                30,                                 // 经验值
                0.05f                               // 价格浮动倍率
        ));
    }
}
