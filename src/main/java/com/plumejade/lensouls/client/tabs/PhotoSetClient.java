package com.plumejade.lensouls.client.tabs;

import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.integration.PhotoSetRegistry;
import com.plumejade.lensouls.integration.PhotographEffectRegistry;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

/**
 * 客户端版「装备照片收集器」：只读 Curios 照片栏（含相册展开），不依赖服务端逻辑。
 * <p>
 * 实现统一收在 common 侧 {@link PhotoSetRegistry#collectInstalledEntities} /
 * {@link PhotoSetRegistry#countInstalledBossPhotos}——照片 tooltip 的套装块（Shift 展开）与
 * 「照片效果」面板共用同一份「已装」口径。这里<b>只做转发</b>，不要再写第二份实现，
 * 否则 tooltip 里「谁已经装了」会和面板/实际生效的统计对不上。
 */
public class PhotoSetClient {

    public static List<String> collectGearEntities(Player player) {
        return PhotoSetRegistry.collectInstalledEntities(player);
    }

    /** 客户端：已装备 Boss 照片的去重种类数（同 Boss 多张只算一次） */
    public static int countBossPhotos(Player player) {
        return PhotoSetRegistry.countInstalledBossPhotos(player);
    }

    /** 客户端：当前各元素抑制等级（供「照片套装效果」界面通用效果块显示） */
    public static Map<ElementDamage, Integer> collectElementLevels(Player player) {
        return PhotographEffectRegistry.countElementLevels(collectGearEntities(player));
    }
}
