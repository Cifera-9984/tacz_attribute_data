package com.core.attribute.tacz.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Per-item overrides for TACZ guns: which ammo the gun feeds on, the projectile's
 * ballistic profile and a base recoil multiplier.
 *
 * <p>Everything is stored in a {@code Gun} child of {@link AttributeData#ROOT} so it
 * travels with the item exactly like the other edits and survives the vanilla/NBT round
 * trip. Nothing is written when a value is left at its default, which keeps an untouched
 * gun's tag clean.</p>
 */
public final class GunOverrides {

    /** Child compound of {@link AttributeData#ROOT} holding the gun overrides. */
    public static final String GUN = "Gun";
    public static final String AMMO_ID = "AmmoId";
    public static final String RECOIL = "Recoil";
    public static final String BULLET_SPEED = "BulletSpeed";
    public static final String BULLET_GRAVITY = "BulletGravity";
    public static final String BULLET_PIERCE = "BulletPierce";
    public static final String BULLET_KNOCKBACK = "BulletKnockback";
    public static final String BULLET_LIFE = "BulletLife";

    /** Ballistic keys in display order, each paired with its short Chinese label. */
    public static final String[][] BULLET_FIELDS = {
            {BULLET_SPEED, "速度"},
            {BULLET_GRAVITY, "重力"},
            {BULLET_PIERCE, "穿透"},
            {BULLET_KNOCKBACK, "击退"},
            {BULLET_LIFE, "存续"}
    };

    public static final double RECOIL_MIN = 0.0;
    public static final double RECOIL_MAX = 3.0;
    public static final double RECOIL_STEP = 0.05;
    /** {@code 1.0} keeps the gun's own recoil, the same convention as the attack-speed line. */
    public static final double RECOIL_DEFAULT = 1.0;

    private GunOverrides() {
    }

    private static CompoundTag gunTag(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !AttributeData.hasData(stack)) {
            return null;
        }
        CompoundTag root = stack.getTag().getCompound(AttributeData.ROOT);
        return root.contains(GUN, 10) ? root.getCompound(GUN) : null;
    }

    /** Ammo id this gun has been switched to, or {@code null} to keep the gun's own. */
    public static ResourceLocation ammoId(ItemStack gun) {
        CompoundTag tag = gunTag(gun);
        if (tag == null || !tag.contains(AMMO_ID, 8)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(AMMO_ID));
    }

    /** Recoil multiplier, {@code 1.0} (the gun's own value) when not overridden. */
    public static double recoilMultiplier(ItemStack gun) {
        CompoundTag tag = gunTag(gun);
        if (tag == null || !tag.contains(RECOIL, 99)) {
            return RECOIL_DEFAULT;
        }
        double value = tag.getDouble(RECOIL);
        return value > 0.0 ? value : RECOIL_DEFAULT;
    }

    /** A ballistic override, empty when the gun's own value should be kept. */
    public static OptionalDouble bullet(ItemStack gun, String key) {
        CompoundTag tag = gunTag(gun);
        if (tag == null || !tag.contains(key, 99)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(tag.getDouble(key));
    }

    /** Saved ammo override as a string, or {@code ""} to keep the gun's own. */
    public static String readAmmo(ItemStack stack) {
        ResourceLocation id = ammoId(stack);
        return id == null ? "" : id.toString();
    }

    /** Only the ballistic fields that were actually saved, for seeding the editor. */
    public static Map<String, Double> readBullet(ItemStack stack) {
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        CompoundTag tag = gunTag(stack);
        if (tag == null) {
            return result;
        }
        for (String[] field : BULLET_FIELDS) {
            if (tag.contains(field[0], 99)) {
                result.put(field[0], tag.getDouble(field[0]));
            }
        }
        return result;
    }

    /**
     * Writes the overrides into {@code root}, creating the child compound only when at
     * least one value deviates from the default.
     */
    public static void write(CompoundTag root, String ammoId, double recoil, Map<String, Double> bullet) {
        if (root == null) {
            return;
        }
        CompoundTag gun = new CompoundTag();
        if (ammoId != null && !ammoId.isBlank()) {
            gun.putString(AMMO_ID, ammoId);
        }
        if (Math.abs(recoil - RECOIL_DEFAULT) > 0.0001) {
            gun.putDouble(RECOIL, Math.max(RECOIL_MIN, Math.min(RECOIL_MAX, recoil)));
        }
        if (bullet != null) {
            for (Map.Entry<String, Double> entry : bullet.entrySet()) {
                if (entry.getValue() != null) {
                    gun.putDouble(entry.getKey(), entry.getValue());
                }
            }
        }
        if (gun.isEmpty()) {
            root.remove(GUN);
        } else {
            root.put(GUN, gun);
        }
    }
}
