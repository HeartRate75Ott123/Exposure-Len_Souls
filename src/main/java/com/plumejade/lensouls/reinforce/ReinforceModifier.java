package com.plumejade.lensouls.reinforce;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.HashMap;
import java.util.Map;

/**
 * 单条强化属性修饰符（数据包 {@code reinforcement} 里的一条 modifiers 项）。
 * <p>
 * 对应原版 {@link AttributeModifier} 所需的全部参数：
 * {@code attribute}（属性 ID）、{@code amount}（数值）、
 * {@code operation}（add_value / add_multiplied_base / add_multiplied_total）、
 * {@code slot}（装备槽组：mainhand / offhand / hand / any / armor / head / chest / legs / feet / body）。
 *
 * @param attribute 目标属性（注册表解析后的 Holder；数据包写错 ID 时该项在加载期被丢弃）
 * @param amount    数值（ADD_VALUE 为绝对值，乘算类为倍率，1.0 = +100%）
 * @param operation 运算方式
 * @param slot      生效槽组
 */
public record ReinforceModifier(Holder<Attribute> attribute, double amount,
                                AttributeModifier.Operation operation, EquipmentSlotGroup slot) {

    /** 默认属性（简写数值形式使用的属性）：主手攻击伤害。 */
    public static final ResourceLocation DEFAULT_ATTRIBUTE_ID =
            ResourceLocation.withDefaultNamespace("generic.attack_damage");
    /** 默认运算：ADD_VALUE。 */
    public static final AttributeModifier.Operation DEFAULT_OPERATION = AttributeModifier.Operation.ADD_VALUE;
    /** 默认槽组：主手。 */
    public static final EquipmentSlotGroup DEFAULT_SLOT = EquipmentSlotGroup.MAINHAND;

    private static final Map<String, AttributeModifier.Operation> OPERATIONS = new HashMap<>();
    private static final Map<String, EquipmentSlotGroup> SLOTS = new HashMap<>();

    static {
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            OPERATIONS.put(op.getSerializedName(), op);
        }
        for (EquipmentSlotGroup group : EquipmentSlotGroup.values()) {
            SLOTS.put(group.getSerializedName(), group);
        }
    }

    /** 按序列化名解析运算方式，未知 → null。 */
    public static AttributeModifier.Operation parseOperation(String name) {
        return name == null ? null : OPERATIONS.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** 按序列化名解析槽组，未知 → null；同时接受 EquipmentSlot 名（head/chest/legs/feet/mainhand/offhand）。 */
    public static EquipmentSlotGroup parseSlot(String name) {
        if (name == null) return null;
        String key = name.toLowerCase(java.util.Locale.ROOT);
        EquipmentSlotGroup direct = SLOTS.get(key);
        if (direct != null) return direct;
        try {
            return EquipmentSlotGroup.bySlot(net.minecraft.world.entity.EquipmentSlot.valueOf(key.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 按注册表 ID 解析属性，未知 → null。 */
    public static Holder<Attribute> parseAttribute(String id) {
        if (id == null) return null;
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(id)).orElse(null);
    }

    /**
     * 生成属性修饰符实例。ID 由材料 ID 派生（{@code lensouls:reinforced/<ns>_<path>}），
     * 保证同一材料对同一物品只写入一条、且与其它来源的修饰符不冲突。
     */
    public AttributeModifier toAttributeModifier(ResourceLocation materialId) {
        return new AttributeModifier(ReinforceHelper.modifierId(materialId), amount, operation);
    }

    /** 把本条修饰符加入 {@link ItemAttributeModifiers} 构建器。 */
    public ItemAttributeModifiers.Builder addTo(ItemAttributeModifiers.Builder builder, ResourceLocation materialId) {
        return builder.add(attribute, toAttributeModifier(materialId), slot);
    }

    /**
     * tooltip 文本，例如 {@code +3 攻击伤害}（绿色）。乘算类显示为百分比。
     */
    public Component describe() {
        String amountText;
        if (operation == AttributeModifier.Operation.ADD_VALUE) {
            amountText = (amount >= 0 ? "+" : "") + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(amount);
        } else {
            double percent = amount * 100.0;
            amountText = (percent >= 0 ? "+" : "") + ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(percent) + "%";
        }
        return Component.literal(amountText + " ")
                .append(Component.translatable(attribute.value().getDescriptionId()))
                .withStyle(ChatFormatting.GREEN);
    }

    /** 槽组后缀文本（灰色），如 {@code (主手)}；ANY 槽组返回空。 */
    public Component slotSuffix() {
        if (slot == EquipmentSlotGroup.ANY) return Component.empty();
        return Component.translatable("item.lensouls.reinforce.slot." + slot.getSerializedName())
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    // ========== 网络序列化（多人同步时随 DatapackSyncPacket 下发） ==========

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(BuiltInRegistries.ATTRIBUTE.getKey(attribute.value()));
        buf.writeDouble(amount);
        buf.writeUtf(operation.getSerializedName());
        buf.writeUtf(slot.getSerializedName());
    }

    /** 解码单条；属性 ID 在客户端注册表中不存在时返回 null（调用方过滤）。 */
    public static ReinforceModifier decode(FriendlyByteBuf buf) {
        ResourceLocation attributeId = buf.readResourceLocation();
        double amount = buf.readDouble();
        String operationName = buf.readUtf();
        String slotName = buf.readUtf();
        Holder<Attribute> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
        AttributeModifier.Operation operation = parseOperation(operationName);
        EquipmentSlotGroup slot = parseSlot(slotName);
        if (attribute == null || operation == null || slot == null) return null;
        return new ReinforceModifier(attribute, amount, operation, slot);
    }
}
