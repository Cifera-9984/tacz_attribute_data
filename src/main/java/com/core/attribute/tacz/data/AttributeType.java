package com.core.attribute.tacz.data;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;

public enum AttributeType {
    HEADSHOT_BONUS("headshot_bonus", "\u7206\u5934\u4f24\u5bb3", ChatFormatting.GOLD, true, List.of("\u7206\u5934", "headshot")),
    PVE_DEFENSE_REDUCTION("pve_defense_reduction", "PVE\u9632\u5fa1\u51cf\u514d", ChatFormatting.AQUA, true, List.of("pve\u9632\u5fa1", "pve\u51cf\u514d", "\u9632\u5fa1\u51cf\u514d", "pve defense")),
    PVE_DAMAGE("pve_damage", "PVE\u4f24\u5bb3", ChatFormatting.RED, false, List.of("pve\u4f24\u5bb3", "pve\u989d\u5916\u4f24\u5bb3", "pve damage")),
    ARMOR_PENETRATION("armor_penetration", "\u62a4\u7532\u7a7f\u900f", ChatFormatting.YELLOW, true, List.of("\u62a4\u7532\u7a7f\u900f", "\u7a7f\u7532", "armor penetration", "penetration")),
    ARMOR("armor", "\u62a4\u7532\u503c", ChatFormatting.BLUE, false, List.of("\u62a4\u7532\u503c", "\u62a4\u7532", "armor")),
    HEALTH("health", "\u751f\u547d", ChatFormatting.LIGHT_PURPLE, false, List.of("\u751f\u547d", "\u751f\u547d\u503c", "health", "max health")),
    DAMAGE("damage", "\u653b\u51fb\u4f24\u5bb3", ChatFormatting.GREEN, false, List.of("\u653b\u51fb\u4f24\u5bb3", "\u4f24\u5bb3", "damage", "attack damage")),
    GUN_LEVEL("gun_level", "\u67aa\u68b0\u7b49\u7ea7", ChatFormatting.GREEN, false, List.of("\u67aa\u68b0\u7b49\u7ea7", "\u7b49\u7ea7", "gun level")),
    DURABILITY("durability", "\u8010\u4e45\u503c", ChatFormatting.GRAY, false, List.of("\u8010\u4e45\u503c", "\u8010\u4e45", "durability")),
    ATTACK_DAMAGE("attack_damage", "\u539f\u7248\u653b\u51fb\u4f24\u5bb3", ChatFormatting.DARK_GREEN, false, List.of("\u539f\u7248\u653b\u51fb\u4f24\u5bb3")),
    ATTACK_SPEED("attack_speed", "\u653b\u51fb\u901f\u5ea6", ChatFormatting.DARK_AQUA, false, List.of("\u653b\u51fb\u901f\u5ea6", "attack speed")),
    ARMOR_TOUGHNESS("armor_toughness", "\u62a4\u7532\u97e7\u6027", ChatFormatting.DARK_BLUE, false, List.of("\u62a4\u7532\u97e7\u6027", "armor toughness")),
    KNOCKBACK_RESISTANCE("knockback_resistance", "\u51fb\u9000\u6297\u6027", ChatFormatting.DARK_GRAY, false, List.of("\u51fb\u9000\u6297\u6027", "knockback resistance")),
    UNBREAKABLE("unbreakable", "\u65e0\u6cd5\u7834\u574f", ChatFormatting.DARK_RED, false, List.of("\u65e0\u6cd5\u7834\u574f", "unbreakable")),
    ENCHANT_GLINT("enchant_glint", "\u9644\u9b54\u5149\u6548", ChatFormatting.LIGHT_PURPLE, false, List.of("\u9644\u9b54\u5149\u6548", "glint")),
    CUSTOM("custom", "Lore", ChatFormatting.DARK_PURPLE, false, List.of("lore", "\u63cf\u8ff0"));

    private final String id;
    private final String displayName;
    private final ChatFormatting color;
    private final boolean percent;
    private final List<String> aliases;

    private AttributeType(String id, String displayName, ChatFormatting color, boolean percent, List<String> aliases) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.percent = percent;
        this.aliases = aliases;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public ChatFormatting color() {
        return this.color;
    }

    public boolean percent() {
        return this.percent;
    }

    /** Switch-like types whose value is simply "on" / "off" rather than a number. */
    public boolean toggle() {
        return this == UNBREAKABLE || this == ENCHANT_GLINT;
    }

    public static AttributeType byId(String id) {
        return Arrays.stream(AttributeType.values()).filter(type -> type.id.equals(id)).findFirst().orElse(CUSTOM);
    }

    public static AttributeType detect(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replace("\uff1a", ":");
        for (AttributeType type : AttributeType.values()) {
            if (type == CUSTOM) continue;
            for (String alias : type.aliases) {
                if (!normalized.contains(alias.toLowerCase(Locale.ROOT))) continue;
                return type;
            }
        }
        return CUSTOM;
    }
}

