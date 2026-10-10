package com.plumejade.lensouls.util;

import com.plumejade.lensouls.ability.handler.CameraInputHandler;
import com.plumejade.lensouls.config.DataPackLoader;
import com.plumejade.lensouls.config.ItemElementActivityLoader;
import com.plumejade.lensouls.damage.ElementDamage;
import com.plumejade.lensouls.enchantment.ModEnchantments;
import com.plumejade.lensouls.integration.ExposureHelper;
import io.github.mortuusars.exposure.world.camera.frame.EntityInFrame;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    /**
     * 「实体 id + 帧里记的位置」定位实体的搜索半径（方块）。
     * <p>
     * 帧里存的是 {@code entity.blockPosition()}（世界坐标），允许实体在拍照→出片之间走动的余量；
     * 只在同一位置的候选里挑最近的一只，所以放宽半径不会被旁边的同类实体顶掉。
     */
    private static final double FRAME_ENTITY_RADIUS = 3.0;

    // ── 照片自身 CustomData 键 ──
    /** 记录的主体实体弱点元素（{@link ElementDamage#getSerializedName()}） */
    public static final String PHOTO_ELEMENT = "lensouls:weakness_element";
    /**
     * 「这张照片已经按新主体口径扫过了」标记。
     * <p>
     * 有它 ⇒ 照片没写 {@link #PHOTO_ELEMENT} 就是<b>刻意不记</b>（主体是友方/玩家，或该生物没有显式弱点），
     * 装机与 GUI 都<b>不许</b>再按实体 id 反查补一个回来；
     * 没它（规则生效前拍的旧照片）⇒ 保留旧的「按实体 id 反查」兜底。
     * 详见 {@link #getRecordedElement} / {@link #resolveElement}。
     */
    public static final String PHOTO_SCANNED = "lensouls:weakness_scanned";
    /** 剩余耐久 */
    public static final String PHOTO_DURABILITY = "lensouls:durability";

    // ── 武器 CustomData 键（沿用旧键名，存档兼容） ──
    /** 已装入武器的照片本体（ItemStack NBT） */
    public static final String WEAPON_PHOTO = "SoulPhotoStack";
    /** 照片主体实体 id */
    public static final String WEAPON_ENTITY = "SoulPhotoEntityId";
    /** 照片记录弱点元素（由照片推导；旧存档缺失时按 {@link #WEAPON_ENTITY} 反查） */
    public static final String WEAPON_ELEMENT = "SoulPhotoWeakness";
    /**
     * 「这张照片刻意没有记录元素」标记（武器侧）。
     * <p>
     * 有它 ⇒ {@link #inspect} 不走「按 {@link #WEAPON_ENTITY} 反查弱点数据」的旧兜底。
     * 为什么需要：那个兜底是给「规则生效前拍的旧照片」补元素的，而照片侧的新标记
     * {@link #PHOTO_SCANNED} 又拦住了 <b>补写</b> 的路径 ⇒ 只剩热路径这一处会把
     * 「主体被当友方跳过」的照片重新算出一个元素，必须在这里也拦一道。
     */
    public static final String WEAPON_NO_ELEMENT = "SoulPhotoNoElement";

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
     * 照片主体：帧内第一个**非玩家、非友方**实体（= Exposure 记录的「离相机最近的主体」，
     * 与 {@link ExposureHelper#getEntityId} 读到的 {@code entities_in_frame[0]} 同口径）。
     * <p>
     * {@code EntitiesInFrameMixin} 保证列表内都是活着的 {@code LivingEntity}，
     * 多部件 boss 的子部件已追溯到父体再补进列表。
     * <p>
     * <b>玩家不算主体（用户口径）</b>：弱点透镜<b>不需要</b>帧内有其他玩家——
     * 玩家既没有实体照片效果条目，拿玩家当主体只会让照片退化（旧照片里还留着持有者自己）。
     * 判定收口在 {@link ExposureHelper#isPlayerEntityId}；「滤镜给队友上增益」那条链路
     * <b>不经过本方法</b>（它直接读帧内实体列表），所以这里跳玩家不会影响给队友加 buff。
     * <p>
     * <b>驯服宠物也不算主体（用户口径：只有野生狼能获取到弱点）</b>：驯服宠物归入友方阵营
     * （分类见 {@link AllyFilter#isTamedPet}，<b>只认 {@code isTame()}、不分是谁的</b>），
     * 它常常离相机最近、挤在 {@code get(0)} 上 ⇒ 跳过它并继续看后面真正的怪。
     * <p>
     * <b>为什么要 live 实体</b>：{@link EntityInFrame} 只存 {@code (id, name, pos, distance)}、
     * <b>没有 UUID</b>（Exposure 的设计：帧是存档数据，不持有实体引用），而「驯没驯服」是
     * <b>实例状态</b>、同一个 {@code minecraft:wolf} 既可能是野生的也可能是某人的狗 ——
     * 光看实体 id 判不出来。所以按「id + 帧里记的 {@link EntityInFrame#pos() 位置}」在
     * 拍摄者所在世界就近定位那只实体（见 {@link #resolveFrameEntity}）。
     * <p>
     * 定位不到时（实体已死/走远/切维度，或调用方拿不到 level）<b>不额外跳过</b>、
     * 也不误判：按原口径把该条当主体——过滤器只回答「明确是友方时跳过」。
     */
    public static ResourceLocation subjectEntityId(Frame frame) {
        return subjectEntityId(frame, null);
    }

    /**
     * @param viewer 拍摄者（取它的 level 做「id + 位置」定位，从而判定驯服宠物）。
     *               为 {@code null}、或 {@link Frame#isTakenBy} 不成立（帧不是它拍的）
     *               时跳过友方判定，只按原口径跳玩家。
     */
    public static ResourceLocation subjectEntityId(Frame frame, LivingEntity viewer) {
        if (frame == null) return null;
        List<EntityInFrame> list = frame.entitiesInFrame();
        if (list == null) return null;
        Level level = viewer != null && frame.isTakenBy(viewer) ? viewer.level() : null;
        for (EntityInFrame entry : list) {
            if (entry == null || entry.id() == null) continue;
            if (ExposureHelper.isPlayerEntityId(entry.id())) continue;
            // 友方不算主体（口径与能力窃取/定格/弹幕一致，定义全在 AllyFilter）
            Entity live = resolveFrameEntity(level, entry);
            if (live != null && AllyFilter.isFriendlyUnit(live)) continue;
            return entry.id();
        }
        return null;
    }

    /**
     * 按帧记录反查那只实体：{@code level} 里该 {@code id}、且离 {@link EntityInFrame#pos()}
     * 最近的一只（定位半径见 {@link #FRAME_ENTITY_RADIUS}）。
     * <p>
     * {@code level} 为空（拿不到视角 / 照片是旧的 NBT 回读）或找不到时返回 {@code null}，
     * 调用方据此<b>放弃友方判定</b>而不是乱判。
     */
    private static Entity resolveFrameEntity(Level level, EntityInFrame entry) {
        if (level == null) return null;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entry.id());
        if (type == null) return null;
        // Frame 里存的是世界坐标方块位（EntityInFrame.of 用的 entity.blockPosition()）
        Vec3 center = Vec3.atCenterOf(entry.pos());
        List<Entity> candidates = level.getEntitiesOfClass(Entity.class,
                new AABB(center, center).inflate(FRAME_ENTITY_RADIUS),
                e -> e.getType() == type);
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            double d = candidate.position().distanceToSqr(center);
            if (d < bestDist) {
                bestDist = d;
                best = candidate;
            }
        }
        return best;
    }

    // ========== 弱点元素 ==========

    /**
     * 实体「被识别出的弱点元素」：{@code entity_weakness} 中**显式配置**且倍率最高的元素。
     * <p>
     * 只认显式配置——{@code DataPackLoader.getWeakness} 对未配置元素会给 0.1 的兜底值，
     * 拿它来「识别弱点」等于任何生物都能认出一个弱点，那不是识别。
     * 弹射物弱点（{@link ElementDamage#PROJECTILE}）不参与：它的活性来自投射物而非武器，
     * 换算成「武器活性」没有意义。
     * <p>
     * <b>遍历顺序 = 数据包里的书写顺序</b>（{@code DataPackLoader} 用 {@link java.util.LinkedHashMap} 保序）：
     * 倍率相同时取**先写的**那个。**不要退回「按 {@code ElementDamage.values()} 枚举顺序遍历」** ——
     * 那等于给平手情况硬编码一个 fire，实测表现就是「明明有水弱点却一律记录成火」。
     *
     * @return 该实体的弱点元素；<b>没有任何显式（非弹射物）弱点时返回 {@code null}</b>
     *         —— 调用方据此**不记录任何弱点**（照片上就不该有这一项）
     */
    public static ElementDamage weaknessElementOf(ResourceLocation entityId) {
        if (entityId == null) return null;
        Map<ElementDamage, Float> weaknesses = DataPackLoader.getAllWeaknesses(entityId);
        if (weaknesses.isEmpty()) return null;

        ElementDamage best = null;
        float bestValue = 0f;
        for (Map.Entry<ElementDamage, Float> entry : weaknesses.entrySet()) {
            ElementDamage element = entry.getKey();
            if (element == ElementDamage.PROJECTILE) continue;
            Float value = entry.getValue();
            if (value == null || value <= 0f) continue;
            if (value > bestValue) {
                best = element;
                bestValue = value;
            }
        }
        return best;
    }

    /**
     * 读取照片记录的元素。
     * <p>
     * 照片 tag 优先；tag 里没有时按主体实体 id 反查弱点数据——但<b>只对「没扫过」的旧照片</b>这么做
     * （见 {@link #hasWeaknessScan}）：新照片没写元素是因为主体被跳过（友方/玩家）或该生物本来就没有
     * 显式弱点，反查等于把「跳过」又撤销回来（拍自家狗 → 武器照样拿到狗的元素活性）。
     */
    public static ElementDamage getRecordedElement(ItemStack photo, RegistryAccess access) {
        CompoundTag tag = tagOf(photo);
        if (tag != null) {
            ElementDamage fromTag = parseElement(tag.getString(PHOTO_ELEMENT));
            if (fromTag != null) return fromTag;
            if (tag.getBoolean(PHOTO_SCANNED)) return null;   // 扫过且没记 ⇒ 刻意不记，不许兜底
        }
        return access == null ? null : weaknessElementOf(ExposureHelper.getEntityId(photo, access));
    }

    /**
     * 照片 tag 优先、其次按实体 id 反查——装机时已知实体 id，省一次组件解析。
     * 反查同样只对旧照片生效（理由见 {@link #getRecordedElement}）。
     */
    public static ElementDamage resolveElement(ItemStack photo, ResourceLocation entityId) {
        CompoundTag tag = tagOf(photo);
        if (tag != null) {
            ElementDamage fromTag = parseElement(tag.getString(PHOTO_ELEMENT));
            if (fromTag != null) return fromTag;
            if (tag.getBoolean(PHOTO_SCANNED)) return null;
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

    /**
     * 这张照片是否已按「跳玩家 + 跳友方」的新主体口径扫过（见 {@link #PHOTO_SCANNED}）。
     * <p>
     * 标记只由出片注入那一刻打上（{@code PhotoInjector} 的 {@code WEAKNESS_LENS} 分支，
     * 直接写进该方法的 tag —— 那边是唯一落盘 CUSTOM_DATA 的地方），
     * 所以「没标记」= 规则生效前拍的旧照片 ⇒ 保留「按实体 id 反查弱点」的旧兜底。
     */
    public static boolean hasWeaknessScan(ItemStack photo) {
        CompoundTag tag = tagOf(photo);
        return tag != null && tag.getBoolean(PHOTO_SCANNED);
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
        // 「照片刻意没记弱点」时不许按实体 id 反查：那张照片的主体被当友方跳过了
        // （拍自家狗 → 反查又会把狗的元素活性还给武器，等于没跳）。见 {@link #WEAPON_NO_ELEMENT}
        boolean noElement = element == null && tag.getBoolean(WEAPON_NO_ELEMENT);
        if (element == null && !noElement && !entityId.isEmpty()) {
            try {
                element = weaknessElementOf(ResourceLocation.parse(entityId));
            } catch (Exception ignored) {
            }
        }
        return new Installed(true, entityId.isEmpty() ? null : entityId, element, noElement);
    }

    /** {@link #inspect} 的结果：{@code present} = 武器上有照片本体 */
    public record Installed(boolean present, String entityId, ElementDamage element, boolean noElement) {
        public static final Installed NONE = new Installed(false, null, null, true);

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
                tag.remove(WEAPON_NO_ELEMENT);
                // 照片自身也留档（旧照片首次装机时补齐，此后无需再查数据包）
                recordElementIfAbsent(photo, element);
            } else {
                // 照片刻意没记元素（主体被跳过 / 该生物没有显式弱点）⇒ 武器也标上「别反查」，
                // 否则 inspect 会按 WEAPON_ENTITY 反查出一个元素，把照片那边的跳过撤销掉。
                tag.remove(WEAPON_ELEMENT);
                tag.putBoolean(WEAPON_NO_ELEMENT, true);
            }
        } else {
            tag.remove(WEAPON_ENTITY);
            tag.remove(WEAPON_ELEMENT);
            tag.remove(WEAPON_NO_ELEMENT);
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
        tag.remove(WEAPON_NO_ELEMENT);
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
