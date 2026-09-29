package com.plumejade.lensouls.util;

import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.integration.ExposureHelper;
import io.github.mortuusars.exposure.world.camera.frame.EntityInFrame;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.Map;

/**
 * 弱点透镜照片的数据口径（单一事实来源）。
 * <p>
 * 一张弱点透镜照片带三样东西：
 * <ol>
 *   <li><b>主体实体</b>：照片自身由 Exposure 记录（{@code photograph_frame.entities_in_frame[0]}），
 *       装到武器上时抄进 {@code SoulPhotoEntityId}，用于对该生物增伤；</li>
 *   <li><b>记录弱点元素</b>：拍照时按主体实体的 {@code entity_weakness} 数据取「倍率最高的显式弱点」
 *       元素，写进照片 {@code lensouls:weakness_element}；</li>
 *   <li><b>耐久</b>：{@code lensouls:durability}，初始 {@link #MAX_DURABILITY}，
 *       照片在武器上每次生效 -1，归零即销毁。</li>
 * </ol>
 * 照片装到武器上后，武器额外获得该元素 {@link #ACTIVITY_LEVEL} 级武器活性
 * （与武器自身的 {@code item_element_activity} 等级取较高者）——见
 * {@link #getActivityLevel(ItemStack, ResourceLocation, ElementDamage)}。
 * <p>
 * 本类只做数据读写，不含事件；事件侧见
 * {@code com.plumejade.lensouls.handler.WeaknessLensHandler}。
 */
public final class WeaknessLensPhoto {

    private WeaknessLensPhoto() {
    }

    /** 弱点透镜照片可生效次数（点数为 0 即销毁） */
    public static final int MAX_DURABILITY = 100;

    /** 照片带给武器的活性等级：视为该元素的 2 级武器活性 */
    public static final int ACTIVITY_LEVEL = 2;

    // ── 照片自身 CustomData 键 ──
    /** 记录的主体实体弱点元素（{@link ElementDamage#getSerializedName()}） */
    public static final String PHOTO_ELEMENT = "lensouls:weakness_element";
    /** 剩余耐久 */
    public static final String PHOTO_DURABILITY = "lensouls:durability";

    // ── 武器 CustomData 键（沿用旧键名，存档兼容） ──
    /** 已装入武器的照片本体（ItemStack NBT） */
    public static final String WEAPON_PHOTO = "SoulPhotoStack";
    /** 照片主体实体 id */
    public static final String WEAPON_ENTITY = "SoulPhotoEntityId";
    /** 照片记录弱点元素（由照片推导；旧存档缺失时按 {@link #WEAPON_ENTITY} 反查） */
    public static final String WEAPON_ELEMENT = "SoulPhotoWeakness";

    // ========== 照片判定 ==========

    /**
     * 是否为可放入剑槽的弱点透镜照片（与 {@code PhotoGuiMenu} 槽位谓词同源）。
     */
    public static boolean isWeaknessLensPhoto(ItemStack stack) {
        return ExposureHelper.isSwordSlotSuitable(stack);
    }

    /**
     * 装机目标：附了摄魂术、且不是相机。
     * <p>
     * 排除两个相机是必须的——相机本身也附摄魂术（拍照靠它），若不排除，
     * 一手相机一手照片右键会被装机拦截，取景器就打不开了。
     * <p>
     * 服务端 {@code WeaknessLensHandler} 与客户端 {@code PhotographInstallMixin} 共用本判定，
     * 保证「服务端装不装」和「客户端要不要吞掉照片查看界面」永远一致。
     */
    public static boolean isInstallTarget(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (CameraInputHandler.isCamera(stack)) return false;
        return ModEnchantments.getSoulPhotographyLevel(player.registryAccess(), stack) > 0;
    }

    /**
     * 帧内第一个实体（= Exposure 记录的「离相机最近的主体」，与
     * {@link ExposureHelper#getEntityId} 读到的 {@code entities_in_frame[0]} 一致）。
     * <p>
     * {@code EntitiesInFrameMixin} 保证列表内都是活着的 {@code LivingEntity}，
     * 多部件 boss 的子部件已追溯到父体再补进列表。
     */
    public static ResourceLocation subjectEntityId(Frame frame) {
        if (frame == null) return null;
        List<EntityInFrame> list = frame.entitiesInFrame();
        if (list == null || list.isEmpty()) return null;
        var first = list.get(0);
        return first != null ? first.id() : null;
    }

    // ========== 弱点元素 ==========

    /**
     * 实体「被识别出的弱点元素」：{@code entity_weakness} 中**显式配置**且倍率最高的元素。
     * <p>
     * 只认显式配置——{@code DataPackLoader.getWeakness} 对未配置元素会给 0.1 的兜底值，
     * 拿它来「识别弱点」等于任何生物都能认出一个弱点，那不是识别。
     * 弹射物弱点（{@link ElementDamage#PROJECTILE}）不参与：它的活性来自投射物而非武器，
     * 换算成「武器活性」没有意义。倍率相同时按枚举声明顺序取前者（结果确定，不随 Map 迭代顺序漂移）。
     */
    public static ElementDamage weaknessElementOf(ResourceLocation entityId) {
        if (entityId == null) return null;
        Map<ElementDamage, Float> weaknesses = DataPackLoader.getAllWeaknesses(entityId);
        if (weaknesses.isEmpty()) return null;

        ElementDamage best = null;
        float bestValue = 0f;
        for (ElementDamage element : ElementDamage.values()) {
            if (element == ElementDamage.PROJECTILE) continue;
            Float value = weaknesses.get(element);
            if (value == null || value <= 0f) continue;
            if (best == null || value > bestValue) {
                best = element;
                bestValue = value;
            }
        }
        return best;
    }

    /** 读取照片记录的元素（照片 tag 优先；旧照片按主体实体 id 反查弱点数据） */
    public static ElementDamage getRecordedElement(ItemStack photo, RegistryAccess access) {
        CompoundTag tag = tagOf(photo);
        if (tag != null) {
            ElementDamage fromTag = parseElement(tag.getString(PHOTO_ELEMENT));
            if (fromTag != null) return fromTag;
        }
        return access == null ? null : weaknessElementOf(ExposureHelper.getEntityId(photo, access));
    }

    /** 照片 tag 优先、其次按实体 id 反查——装机时已知实体 id，省一次组件解析 */
    public static ElementDamage resolveElement(ItemStack photo, ResourceLocation entityId) {
        CompoundTag tag = tagOf(photo);
        if (tag != null) {
            ElementDamage fromTag = parseElement(tag.getString(PHOTO_ELEMENT));
            if (fromTag != null) return fromTag;
        }
        return weaknessElementOf(entityId);
    }

    /** 把识别出的弱点元素补写进照片（已有则不动），并补上缺省耐久 */
    public static void recordElementIfAbsent(ItemStack photo, ElementDamage element) {
        if (photo == null || photo.isEmpty() || element == null) return;
        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean changed = false;
        if (tag.getString(PHOTO_ELEMENT).isEmpty()) {
            tag.putString(PHOTO_ELEMENT, element.getSerializedName());
            changed = true;
        }
        if (!tag.contains(PHOTO_DURABILITY)) {
            tag.putInt(PHOTO_DURABILITY, MAX_DURABILITY);
            changed = true;
        }
        if (changed) photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ========== 耐久 ==========

    /** 剩余耐久（无标记 = 新照片/旧照片，按满耐久计） */
    public static int getDurability(ItemStack photo) {
        int value = getDurabilityOrNone(photo);
        return value < 0 ? MAX_DURABILITY : value;
    }

    /**
     * 剩余耐久；**没有耐久标记时返回 -1**。
     * <p>
     * 物品栏耐久条（{@code WeaknessLensDurabilityDecorator}）用它区分「带耐久的弱点透镜照片」
     * 与其它照片，省掉一次「是不是弱点透镜」的判定。
     */
    public static int getDurabilityOrNone(ItemStack photo) {
        CompoundTag tag = tagOf(photo);
        if (tag == null || !tag.contains(PHOTO_DURABILITY)) return -1;
        return Mth.clamp(tag.getInt(PHOTO_DURABILITY), 0, MAX_DURABILITY);
    }

    public static void setDurability(ItemStack photo, int value) {
        if (photo == null || photo.isEmpty()) return;
        CompoundTag tag = photo.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(PHOTO_DURABILITY, Mth.clamp(value, 0, MAX_DURABILITY));
        photo.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ========== 武器侧读写 ==========

    /** 武器上已装的照片（无则空栈） */
    public static ItemStack getInstalledPhoto(ItemStack weapon, RegistryAccess access) {
        CompoundTag tag = tagOf(weapon);
        if (tag == null || !tag.contains(WEAPON_PHOTO, Tag.TAG_COMPOUND) || access == null) return ItemStack.EMPTY;
        return ItemStack.parseOptional(access, tag.getCompound(WEAPON_PHOTO));
    }

    /** 武器上照片记录的元素（专用键优先，旧存档按实体 id 反查弱点数据） */
    public static ElementDamage getInstalledElement(ItemStack weapon) {
        return inspect(weapon).element();
    }

    /** 照片提供的活性等级（该元素与照片记录一致时 = {@link #ACTIVITY_LEVEL}，否则 0） */
    public static int getPhotoActivityLevel(ItemStack weapon, ElementDamage element) {
        ElementDamage installed = getInstalledElement(weapon);
        return installed != null && installed == element ? ACTIVITY_LEVEL : 0;
    }

    /**
     * 武器照片摘要（「有没有装照片 + 主体实体 + 记录元素」一次读出）。
     * <p>
     * 伤害结算是全服热路径（每个实体的每次伤害都会跑），逐项查询会把同一份武器 NBT
     * 反复 {@code copyTag()}；这里一次读完供调用方复用。
     */
    public static Installed inspect(ItemStack weapon) {
        CompoundTag tag = installedTagOf(weapon);
        if (tag == null) return Installed.NONE;

        String entityId = tag.getString(WEAPON_ENTITY);
        ElementDamage element = parseElement(tag.getString(WEAPON_ELEMENT));
        if (element == null && !entityId.isEmpty()) {
            try {
                element = weaknessElementOf(ResourceLocation.parse(entityId));
            } catch (Exception ignored) {
            }
        }
        return new Installed(true, entityId.isEmpty() ? null : entityId, element);
    }

    /** {@link #inspect} 的结果：{@code present} = 武器上有照片本体 */
    public record Installed(boolean present, String entityId, ElementDamage element) {
        public static final Installed NONE = new Installed(false, null, null);

        /** 有无可用的「记录元素」 */
        public boolean hasElement() {
            return element != null;
        }
    }

    /**
     * 与 {@link #inspect} 相同，但额外交代「照片当前真的在生效」：武器仍附有摄魂术。
     * <p>
     * 装机入口要求摄魂术，但事后被砂轮祛魔/换物品时武器 NBT 还在。
     * 增伤侧（{@code PhotoDamageHandler}）本来就查附魔，元素活性与耐久消耗必须同口径，
     * 否则会留下「祛了魔照样吃照片活性」的口子。
     */
    public static Installed inspectActive(ItemStack weapon, RegistryAccess access) {
        Installed installed = inspect(weapon);
        if (!installed.present() || access == null) return Installed.NONE;
        if (ModEnchantments.getSoulPhotographyLevel(access, weapon) <= 0) return Installed.NONE;
        return installed;
    }

    /**
     * 武器最终元素活性等级 = max(物品自身 {@code item_element_activity} 等级, 照片提供的 2 级)。
     * <p>
     * 需求口径的唯一定义处：「安装在武器上时获得一层该加成 —— 被认为是该元素的 2 级武器活性；
     * 如果武器自身有更高等级，则按本身的」。tooltip 直接调本方法；
     * 伤害结算与 BOSS 限伤绕过是全服热路径，为避免逐元素重复读武器 NBT，
     * 在那两处只把 {@link #inspect} 的结果读一次再就地取 max（与这里同一算法）。
     */
    public static int getActivityLevel(ItemStack weapon, ResourceLocation itemId, ElementDamage element) {
        int base = itemId == null ? 0 : ItemElementActivityLoader.getLevel(itemId, element);
        int photo = getPhotoActivityLevel(weapon, element);
        return Math.max(base, photo);
    }

    /** 武器的物品 id（活性配置查表用） */
    public static ResourceLocation itemIdOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * 把照片装入武器：写入照片本体（**数量固定为 1**，避免整堆照片被一并「装」进武器）+ 主体实体 id + 记录元素。
     * <p>
     * 数量固定 1 是右击装机路径的关键：照片堆可能一次拿 1 个装、剩下的仍在玩家手里，
     * 若连数量一起存进武器，武器上的那份会变成一整堆。
     */
    public static void writeInstalled(ItemStack weapon, ItemStack photo, RegistryAccess access) {
        if (weapon == null || weapon.isEmpty() || photo == null || photo.isEmpty() || access == null) return;

        ItemStack single = photo.copyWithCount(1);
        CompoundTag tag = weapon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        CompoundTag photoTag = new CompoundTag();
        tag.put(WEAPON_PHOTO, single.save(access, photoTag));

        ResourceLocation entityId = ExposureHelper.getEntityId(single, access);
        if (entityId != null) {
            tag.putString(WEAPON_ENTITY, entityId.toString());
            ElementDamage element = resolveElement(single, entityId);
            if (element != null) {
                tag.putString(WEAPON_ELEMENT, element.getSerializedName());
                // 照片自身也留档（旧照片首次装机时补齐，此后无需再查数据包）
                recordElementIfAbsent(photo, element);
            } else {
                tag.remove(WEAPON_ELEMENT);
            }
        } else {
            tag.remove(WEAPON_ENTITY);
            tag.remove(WEAPON_ELEMENT);
        }
        weapon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 用改装后的照片覆盖武器上的照片本体（耐久扣减用；其余键不动） */
    public static void updateInstalledPhoto(ItemStack weapon, ItemStack photo, RegistryAccess access) {
        if (weapon == null || weapon.isEmpty() || photo == null || photo.isEmpty() || access == null) return;
        CompoundTag tag = weapon.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag photoTag = new CompoundTag();
        tag.put(WEAPON_PHOTO, photo.save(access, photoTag));
        weapon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** 清空武器上的照片数据（照片取出 / 耐久耗尽销毁） */
    public static void clearInstalled(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) return;
        CustomData data = weapon.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        tag.remove(WEAPON_PHOTO);
        tag.remove(WEAPON_ENTITY);
        tag.remove(WEAPON_ELEMENT);
        weapon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ========== 内部 ==========

    private static CompoundTag tagOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /**
     * 装了照片时取出武器 NBT；没装照片返回 null（**不复制 NBT**）。
     * <p>
     * {@link #inspect} 会在每次伤害结算里被调用，先做一次组件键存在性判断，
     * 避免为「拿着普通武器（甚至带弹药/强化 NBT 的武器）打人」这件事复制整份 NBT。
     */
    private static CompoundTag installedTagOf(ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) return null;
        CustomData data = weapon.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(WEAPON_PHOTO)) return null;
        CompoundTag tag = data.copyTag();
        return tag.contains(WEAPON_PHOTO, Tag.TAG_COMPOUND) ? tag : null;
    }

    private static ElementDamage parseElement(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return ElementDamage.byName(name);
        } catch (Exception e) {
            return null;
        }
    }
}
