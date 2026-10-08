package com.core.attribute.tacz.data;

import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.item.GunTooltipPart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

public final class AttributeData {
    public static final String ROOT = "TaczAttributeData";
    public static final String LINES = "Lines";
    public static final String HIDE_VANILLA_PANEL = "HideVanillaPanel";
    public static final String VANILLA_HIDE_FLAGS = "VanillaHideFlags";
    public static final String HIDE_MAIN_HAND_ATTRIBUTES = "HideMainHandAttributes";
    /** Derived flag inside our root: hide the mod's own attribute lines in the tooltip. */
    public static final String HIDE_ATTRIBUTE_LABELS = "HideAttributeLabels";
    public static final String HIDE_FLAGS = "HideFlags";
    /** Derived flag inside our root: render the enchantment glint. */
    public static final String GLINT = "Glint";
    /** Vanilla tag that makes an item unbreakable. */
    public static final String VANILLA_UNBREAKABLE = "Unbreakable";
    public static final String RUNTIME_DAMAGE = "Damage";
    public static final String RUNTIME_PVE_DAMAGE = "PveDamage";
    public static final String RUNTIME_ARMOR_PENETRATION = "ArmorPenetration";
    public static final String RUNTIME_HEADSHOT = "Headshot";
    private static final int VANILLA_MODIFIERS_HIDE_FLAG = 2;
    private static final int VANILLA_HIDE_FLAGS_MASK = 255;
    private static final int TACZ_TOOLTIP_HIDE_MASK = AttributeData.buildTaczTooltipHideMask();
    private static final Pattern NUMBER = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private AttributeData() {
    }

    public static boolean hasData(ItemStack stack) {
        return !stack.isEmpty() && stack.hasTag() && stack.getTag().contains(ROOT, 10);
    }

    public static boolean hasVisibleLines(ItemStack stack) {
        return AttributeData.getLines(stack).stream().anyMatch(line -> !line.hidden() && !line.text().isBlank());
    }

    public static CompoundTag copyRoot(ItemStack stack) {
        if (!AttributeData.hasData(stack)) {
            CompoundTag root = new CompoundTag();
            root.put(LINES, (Tag)new ListTag());
            root.putBoolean(HIDE_VANILLA_PANEL, true);
            return root;
        }
        return stack.getTag().getCompound(ROOT).copy();
    }

    public static void writeRoot(ItemStack stack, CompoundTag root) {
        CompoundTag sanitized;
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag compoundTag = sanitized = root == null ? new CompoundTag() : root.copy();
        if (!sanitized.contains(LINES, 9)) {
            sanitized.put(LINES, (Tag)new ListTag());
        }
        if (sanitized.contains(VANILLA_HIDE_FLAGS, 3)) {
            sanitized.putInt(VANILLA_HIDE_FLAGS, sanitized.getInt(VANILLA_HIDE_FLAGS) & 0xFF);
        }
        // Unbreakable and glint are stored as derived flags so the mixins and the vanilla
        // tag stay in sync with whatever the editor wrote into the line list.
        boolean unbreakable = lineFlag(sanitized, AttributeType.UNBREAKABLE);
        sanitized.putBoolean(GLINT, lineFlag(sanitized, AttributeType.ENCHANT_GLINT));
        stack.getOrCreateTag().put(ROOT, (Tag)sanitized);
        CompoundTag stackTag = stack.getOrCreateTag();
        if (unbreakable) {
            stackTag.putBoolean(VANILLA_UNBREAKABLE, true);
        } else {
            stackTag.remove(VANILLA_UNBREAKABLE);
        }
        AttributeData.applyTooltipHideFlags(stack, sanitized.getBoolean(HIDE_VANILLA_PANEL));
    }

    /** Whether the given switch type is present and switched on inside {@code root}. */
    private static boolean lineFlag(CompoundTag root, AttributeType type) {
        ListTag list = root.getList(LINES, 10);
        for (int i = 0; i < list.size(); ++i) {
            CompoundTag tag = list.getCompound(i);
            AttributeType stored = AttributeType.byId(tag.getString(AttributeLine.TAG_TYPE));
            if (stored != type) {
                continue;
            }
            String value = tag.getString(AttributeLine.TAG_TEXT);
            int colon = value.replace('\uff1a', ':').indexOf(58);
            String tail = colon >= 0 ? value.substring(colon + 1) : "";
            return AttributeData.parseToggle(tail);
        }
        return false;
    }

    /** Lenient "on/off" parsing: an empty value counts as on because a bare line means enabled. */
    public static boolean parseToggle(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isEmpty()) {
            return true;
        }
        return switch (value) {
            case "off", "false", "0", "no", "\u5173", "\u5426", "\u5173\u95ed" -> false;
            default -> true;
        };
    }

    /** {@code true} when the stack is flagged to render the enchantment glint. */
    public static boolean hasGlint(ItemStack stack) {
        return AttributeData.hasData(stack) && stack.getTag().getCompound(ROOT).getBoolean(GLINT);
    }

    /** {@code true} when the stack carries a visible line of the given type. */
    public static boolean hasLine(ItemStack stack, AttributeType type) {
        for (AttributeLine line : AttributeData.getLines(stack)) {
            if (line.type() == type && !line.hidden()) {
                return true;
            }
        }
        return false;
    }

    public static List<AttributeLine> getLines(ItemStack stack) {
        ArrayList<AttributeLine> lines = new ArrayList<AttributeLine>();
        if (!AttributeData.hasData(stack)) {
            return lines;
        }
        ListTag list = stack.getTag().getCompound(ROOT).getList(LINES, 10);
        for (int i = 0; i < list.size(); ++i) {
            lines.add(AttributeLine.fromTag(list.getCompound(i)));
        }
        return lines;
    }

    public static void setLines(CompoundTag root, List<AttributeLine> lines) {
        ListTag list = new ListTag();
        for (AttributeLine line : lines) {
            if (line == null || !line.hasEditorContent()) continue;
            list.add(line.toTag());
        }
        root.put(LINES, (Tag)list);
    }

    public static boolean hideVanillaPanel(ItemStack stack) {
        if (!AttributeData.hasData(stack)) {
            return false;
        }
        return stack.getTag().getCompound(ROOT).getBoolean(HIDE_VANILLA_PANEL);
    }

    public static int hideFlagMask(ItemStack stack) {
        int flags;
        CompoundTag root;
        if (stack.isEmpty()) {
            return 0;
        }
        if (AttributeData.hasData(stack) && (root = stack.getTag().getCompound(ROOT)).contains(VANILLA_HIDE_FLAGS, 3)) {
            return root.getInt(VANILLA_HIDE_FLAGS) & 0xFF;
        }
        int n = flags = stack.hasTag() ? stack.getTag().getInt(HIDE_FLAGS) : 0;
        if (AttributeData.hasData(stack) && AttributeData.hideVanillaPanel(stack)) {
            flags &= 0xFFFFFFFD;
            if (stack.getItem() instanceof IGun) {
                flags &= ~TACZ_TOOLTIP_HIDE_MASK;
            }
        }
        return flags & 0xFF;
    }

    public static void setHideFlagMask(CompoundTag root, int flags) {
        root.putInt(VANILLA_HIDE_FLAGS, flags & 0xFF);
    }

    /**
     * Whether the vanilla "when in main hand" attribute block is hidden. Defaults to
     * hidden: an item without an explicit setting shows no vanilla attribute modifiers,
     * and the editor's toggle switches them back on.
     */
    public static boolean hideMainHandAttributes(ItemStack stack) {
        if (!AttributeData.hasData(stack)) {
            return true;
        }
        CompoundTag root = stack.getTag().getCompound(ROOT);
        return !root.contains(HIDE_MAIN_HAND_ATTRIBUTES) || root.getBoolean(HIDE_MAIN_HAND_ATTRIBUTES);
    }

    public static void setHideMainHandAttributes(CompoundTag root, boolean hide) {
        root.putBoolean(HIDE_MAIN_HAND_ATTRIBUTES, hide);
    }

    /**
     * Whether the mod's own attribute lines are hidden. Defaults to hidden, so a freshly
     * edited item only shows them after the editor's "属性标签" switch is turned on.
     */
    public static boolean hideAttributeLabels(ItemStack stack) {
        if (!AttributeData.hasData(stack)) {
            return true;
        }
        CompoundTag root = stack.getTag().getCompound(ROOT);
        return !root.contains(HIDE_ATTRIBUTE_LABELS) || root.getBoolean(HIDE_ATTRIBUTE_LABELS);
    }

    public static void setHideAttributeLabels(CompoundTag root, boolean hide) {
        root.putBoolean(HIDE_ATTRIBUTE_LABELS, hide);
    }

    // ---- enchantments --------------------------------------------------

    /** Enchantment id used inside the save payload: {@code {id, lvl}} per entry. */
    public static final String ENCHANTMENTS = "Enchantments";
    public static final String ENCHANT_ID = "id";
    public static final String ENCHANT_LVL = "lvl";

    /** Ordered snapshot of the enchantments currently on the stack. */
    public static LinkedHashMap<Enchantment, Integer> readEnchantments(ItemStack stack) {
        LinkedHashMap<Enchantment, Integer> result = new LinkedHashMap<>();
        if (stack.isEmpty()) {
            return result;
        }
        result.putAll(EnchantmentHelper.getEnchantments(stack));
        return result;
    }

    /** Serialises an enchantment map into a {@code ListTag} of {@code {id, lvl}} compounds. */
    public static ListTag writeEnchantTag(Map<Enchantment, Integer> enchantments) {
        ListTag list = new ListTag();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey());
            if (id == null) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString(ENCHANT_ID, id.toString());
            tag.putInt(ENCHANT_LVL, Math.max(1, entry.getValue()));
            list.add(tag);
        }
        return list;
    }

    /** Replaces every enchantment on the stack with the ones described by {@code list}. */
    public static void applyEnchantTag(ItemStack stack, ListTag list) {
        if (stack.isEmpty()) {
            return;
        }
        LinkedHashMap<Enchantment, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(ENCHANT_ID));
            if (id == null) {
                continue;
            }
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
            if (enchantment == null) {
                continue;
            }
            result.put(enchantment, Math.max(1, Math.min(tag.getInt(ENCHANT_LVL), enchantment.getMaxLevel())));
        }
        // A single call rewrites the tag in place (no repair-cost side effects, unlike
        // repeatedly calling ItemStack#enchant).
        EnchantmentHelper.setEnchantments(result, stack);
    }

    public static void applyTooltipHideFlags(ItemStack stack, boolean hide) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        int managedMask = 255;
        if (stack.getItem() instanceof IGun) {
            managedMask |= TACZ_TOOLTIP_HIDE_MASK;
        }
        int flags = tag.getInt(HIDE_FLAGS) & ~managedMask;
        flags |= AttributeData.hideFlagMask(stack);
        if (hide) {
            flags |= 2;
            if (stack.getItem() instanceof IGun) {
                flags |= TACZ_TOOLTIP_HIDE_MASK;
            }
        }
        if (flags == 0) {
            tag.remove(HIDE_FLAGS);
        } else {
            tag.putInt(HIDE_FLAGS, flags);
        }
    }

    public static OptionalDouble firstValue(ItemStack stack, AttributeType type) {
        for (AttributeLine line : AttributeData.getLines(stack)) {
            OptionalDouble value;
            if (line.type() != type || line.hidden() || !(value = AttributeData.parseValue(line)).isPresent()) continue;
            return value;
        }
        return OptionalDouble.empty();
    }

    public static OptionalDouble sumValue(ItemStack stack, AttributeType type) {
        double total = 0.0;
        boolean found = false;
        for (AttributeLine line : AttributeData.getLines(stack)) {
            OptionalDouble value;
            if (line.type() != type || line.hidden() || !(value = AttributeData.parseValue(line)).isPresent()) continue;
            total += value.getAsDouble();
            found = true;
        }
        return found ? OptionalDouble.of(total) : OptionalDouble.empty();
    }

    public static double sumWorn(LivingEntity entity, AttributeType type) {
        double total = 0.0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            OptionalDouble value;
            if (slot.getType() != EquipmentSlot.Type.ARMOR || !(value = AttributeData.sumValue(entity.getItemBySlot(slot), type)).isPresent()) continue;
            total += value.getAsDouble();
        }
        return total;
    }

    /**
     * Scales a weapon's own melee cooldown by the attack-speed line, which for weapons
     * that manage their own cooldown (LR Tactical) acts as a speed multiplier:
     * {@code 1.0} keeps the original, {@code 2.0} halves it, {@code 0.5} doubles it.
     *
     * <p>The weapon's base feel is therefore preserved - the same entered value produces
     * a different cooldown for a fast knife and a slow hammer - instead of collapsing
     * every weapon onto one absolute cooldown.</p>
     *
     * @return the scaled cooldown, or {@code 0} to leave the weapon's own value alone
     *         (no attack-speed line, a non-positive multiplier, or no own cooldown)
     */
    public static int scaledMeleeCooldown(ItemStack stack, int originalCooldown) {
        OptionalDouble multiplier = AttributeData.sumValue(stack, AttributeType.ATTACK_SPEED);
        if (multiplier.isEmpty() || multiplier.getAsDouble() <= 0.0 || originalCooldown <= 0) {
            return 0;
        }
        long ticks = Math.round(originalCooldown / multiplier.getAsDouble());
        return (int) Math.max(1L, Math.min(400L, ticks));
    }

    public static OptionalDouble parseValue(AttributeLine line) {
        String raw = line.valueText().isBlank() ? line.text() : line.valueText();
        Matcher matcher = NUMBER.matcher(raw);
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }
        double value = Double.parseDouble(matcher.group());
        boolean percentSign = raw.contains("%");
        if (line.type() == AttributeType.HEADSHOT_BONUS) {
            if (percentSign || value > 10.0) {
                value /= 100.0;
            }
            return OptionalDouble.of(value);
        }
        if (line.type().percent() && (percentSign || value > 1.0)) {
            value /= 100.0;
        }
        return OptionalDouble.of(value);
    }

    private static int buildTaczTooltipHideMask() {
        int mask = 0;
        for (GunTooltipPart part : GunTooltipPart.values()) {
            mask |= part.getMask();
        }
        return mask;
    }
}

