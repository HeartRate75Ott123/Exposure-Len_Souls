package com.plumejade.lensouls.feather;

import com.plumejade.lensouls.LenSouls;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * 7 款诅咒的定义表 + 掩码布局。
 * <p>
 * <b>掩码位序</b>（与 lang 的 eN 序号一致）：
 * <pre>
 * ① 荒厄遗咒    0..2     (3)
 * ② 扭曲之人    3..5     (3)
 * ③ 元素觉醒者  6..7     (2)
 * ④ 折翼沉渊    8..19    (12)
 * ⑤ 断时炉心   20..42    (23)
 * ⑥ 终焉秘仪   43..49    (7)
 * ⑦ 零号誓炉   50..63    (14 = 7 诅咒 + 7 祝福)
 * </pre>
 * 合计 57 条诅咒（⑦ 的 7 条祝福是增益、不计入 57），但**掩码占满 64 位**——正好一个 {@code long}。
 * <p>
 * 这里刻意**不引用 ModItems**（只用注册名反查）：否则 {@code ModItems} 的静态初始化会与
 * {@code CurseDefs} 互相触发，拿到未初始化完的 {@code DeferredItem}（null）。
 */
public final class CurseDefs {

    public static final CurseDef HARDMAN = new CurseDef("feather_hardman", 3, 0, true, false, 0, Set.of(), Set.of());
    public static final CurseDef TWITCHER = new CurseDef("feather_twitcher", 3, 3, true, false, 0, Set.of(), Set.of());
    public static final CurseDef ELEMENTRISE = new CurseDef("feather_elementrise", 2, 6, true, false, 0, Set.of(), Set.of());
    /** ④ 的「虚无的承诺」没有反转条件 ⇒ Shift 视图不展开（noReverse = 第 1 条） */
    public static final CurseDef ABYSS = new CurseDef("feather_abyss", 12, 8, false, false, 0, Set.of(), Set.of(1));
    public static final CurseDef TIMECORE = new CurseDef("feather_timecore", 23, 20, false, true, 0, Set.of(), Set.of());
    public static final CurseDef FINALRITE = new CurseDef("feather_finalrite", 7, 43, false, false, 0, Set.of(), Set.of());
    /** ⑦ 双刃：1..7 是诅咒、8..14 是祝福 */
    public static final CurseDef ZEROFORGE = new CurseDef("feather_zeroforge", 14, 50, false, false, 7, Set.of(), Set.of());

    public static final List<CurseDef> ALL =
            List.of(HARDMAN, TWITCHER, ELEMENTRISE, ABYSS, TIMECORE, FINALRITE, ZEROFORGE);

    /** 掩码总位数（一个 long 刚好装下 64 位） */
    public static final int MASK_BITS = 64;

    private CurseDefs() {
    }

    public static CurseDef byId(String id) {
        if (id == null) return null;
        for (CurseDef def : ALL) {
            if (def.id().equals(id)) return def;
        }
        return null;
    }

    /** 由物品反查定义（非本模组的物品返回 null，不做注册表全表扫描） */
    public static CurseDef byItem(Item item) {
        if (item == null) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !LenSouls.MODID.equals(id.getNamespace())) return null;
        return byId(id.getPath());
    }

    /** 该款对应的物品（未注册时返回 null） */
    public static Item itemOf(CurseDef def) {
        if (def == null) return null;
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(LenSouls.MODID, def.id()));
    }

    /** 该款的展示用物品堆（诅咒列表的图标 / 悬停 tooltip 用） */
    public static ItemStack stackOf(CurseDef def) {
        Item item = itemOf(def);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
