package com.core.attribute.tacz.data;

import com.google.common.collect.Multimap;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.ExtraDamage;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Reads the real, current value of an item so the editor can prefill the rows for an
 * item that has never been edited before. Without this the first opening showed empty
 * inputs, which was reported as a bug.
 */
public final class ItemProbe {

    /** The player's base values, so totals can be shown the way the editor applies them. */
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0;

    private ItemProbe() {
    }

    /** The value text the editor should show for {@code type}, or {@code ""} if unknown. */
    public static String currentValue(ItemStack stack, AttributeType type) {
        if (stack.isEmpty()) {
            return "";
        }
        switch (type) {
            case DURABILITY -> {
                int max = stack.getMaxDamage();
                return max > 0 ? String.valueOf(max) : "";
            }
            case DAMAGE -> {
                if (stack.getItem() instanceof IGun) {
                    Float damage = bulletDamage(stack);
                    return damage == null ? "" : number(damage);
                }
                return number(modifierValue(stack, Attributes.ATTACK_DAMAGE));
            }
            case ARMOR -> {
                return number(modifierValue(stack, Attributes.ARMOR));
            }
            case HEALTH -> {
                return number(modifierValue(stack, Attributes.MAX_HEALTH));
            }
            case ARMOR_PENETRATION -> {
                Float ignore = extraDamage(stack, true);
                return ignore == null ? "" : number(ignore * 100f) + "%";
            }
            case HEADSHOT_BONUS -> {
                Float bonus = extraDamage(stack, false);
                return bonus == null ? "" : number(bonus * 100f) + "%";
            }
            case GUN_LEVEL -> {
                IGun gun = IGun.getIGunOrNull(stack);
                if (gun == null) {
                    return "";
                }
                int level = gun.getLevel(stack);
                return level > 0 ? String.valueOf(level) : "";
            }
            case ATTACK_DAMAGE -> {
                return total(modifierValue(stack, Attributes.ATTACK_DAMAGE), PLAYER_BASE_ATTACK_DAMAGE);
            }
            case ATTACK_SPEED -> {
                return total(modifierValue(stack, Attributes.ATTACK_SPEED), PLAYER_BASE_ATTACK_SPEED);
            }
            case ARMOR_TOUGHNESS -> {
                return number(modifierValue(stack, Attributes.ARMOR_TOUGHNESS));
            }
            case KNOCKBACK_RESISTANCE -> {
                return number(modifierValue(stack, Attributes.KNOCKBACK_RESISTANCE));
            }
            case UNBREAKABLE -> {
                return stack.hasTag() && stack.getTag().getBoolean("Unbreakable") ? "开" : "";
            }
            case ENCHANT_GLINT -> {
                return stack.hasFoil() ? "开" : "";
            }
            default -> {
                return "";
            }
        }
    }

    /** {@code true} when the item actually carries a meaningful value for {@code type}. */
    public static boolean hasValue(ItemStack stack, AttributeType type) {
        return !currentValue(stack, type).isBlank();
    }

    /** The gun's own ammo id (its default main ammo), or {@code null} for a non-gun. */
    public static ResourceLocation currentAmmoId(ItemStack stack) {
        GunData data = gunData(stack);
        return data == null ? null : data.getAmmoId();
    }

    /**
     * A display stack for an ammo id, built the same way TACZ builds ammo stacks for its
     * own tooltips: the ammo type lives in the item's NBT, not in the registry, so the id
     * is resolved through {@link AmmoItemBuilder}.
     */
    public static ItemStack ammoStack(ResourceLocation ammoId) {
        if (ammoId == null) {
            return ItemStack.EMPTY;
        }
        try {
            return AmmoItemBuilder.create().setId(ammoId).build();
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /** The gun's own bullet data, or {@code null} for a non-gun / missing index. */
    public static BulletData currentBullet(ItemStack stack) {
        GunData data = gunData(stack);
        return data == null ? null : data.getBulletData();
    }

    /** One ballistic field of the gun's own bullet data, preformatted for the editor. */
    public static String currentBulletValue(ItemStack stack, String key) {
        BulletData bullet = currentBullet(stack);
        if (bullet == null) {
            return "";
        }
        return switch (key) {
            case GunOverrides.BULLET_SPEED -> number(bullet.getSpeed());
            case GunOverrides.BULLET_GRAVITY -> number(bullet.getGravity());
            case GunOverrides.BULLET_PIERCE -> String.valueOf(bullet.getPierce());
            case GunOverrides.BULLET_KNOCKBACK -> number(bullet.getKnockback());
            case GunOverrides.BULLET_LIFE -> number(bullet.getLifeSecond());
            default -> "";
        };
    }

    // ---- helpers -------------------------------------------------------

    private static GunData gunData(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return null;
        }
        ResourceLocation gunId = gun.getGunId(stack);
        if (gunId == null) {
            return null;
        }
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gunId);
        return index.map(CommonGunIndex::getGunData).orElse(null);
    }

    private static Float bulletDamage(ItemStack stack) {
        GunData data = gunData(stack);
        if (data == null || data.getBulletData() == null) {
            return null;
        }
        BulletData bullet = data.getBulletData();
        return bullet.getDamageAmount();
    }

    private static Float extraDamage(ItemStack stack, boolean armorIgnore) {
        GunData data = gunData(stack);
        if (data == null || data.getBulletData() == null) {
            return null;
        }
        ExtraDamage extra = data.getBulletData().getExtraDamage();
        if (extra == null) {
            return null;
        }
        return armorIgnore ? extra.getArmorIgnore() : extra.getHeadShotMultiplier();
    }

    private static String number(Double value) {
        return value == null ? "" : number(value.doubleValue());
    }

    /** Total value of an attribute, i.e. the player's base plus the item's modifiers. */
    private static String total(Double modifierSum, double base) {
        return modifierSum == null ? "" : number(modifierSum + base);
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private static Double modifierValue(ItemStack stack, Attribute attribute) {
        double total = 0.0;
        boolean found = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
            for (AttributeModifier modifier : modifiers.get(attribute)) {
                if (modifier.getOperation() != AttributeModifier.Operation.ADDITION) {
                    continue;
                }
                total += modifier.getAmount();
                found = true;
            }
        }
        return found ? total : null;
    }
}