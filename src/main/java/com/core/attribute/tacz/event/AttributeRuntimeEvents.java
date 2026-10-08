package com.core.attribute.tacz.event;

import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.data.AttributeType;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.item.IGun;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tacz_attribute_data")
public final class AttributeRuntimeEvents {
    private static final Map<LivingEntity, ArmorBypassState> ARMOR_BYPASS = new WeakHashMap<LivingEntity, ArmorBypassState>();

    private AttributeRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !AttributeData.hasData(stack)) {
            return;
        }
        EquipmentSlot slot = event.getSlotType();
        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
            AttributeRuntimeEvents.applyArmorAttributes(stack, event);
        } else if (slot == EquipmentSlot.MAINHAND) {
            AttributeRuntimeEvents.applyWeaponAttributes(stack, event);
        }
    }

    private static void applyArmorAttributes(ItemStack stack, ItemAttributeModifierEvent event) {
        AttributeData.sumValue(stack, AttributeType.ARMOR).ifPresent(value -> {
            event.removeAttribute(Attributes.ARMOR);
            event.removeAttribute(Attributes.ARMOR_TOUGHNESS);
            event.removeAttribute(Attributes.KNOCKBACK_RESISTANCE);
            event.addModifier(Attributes.ARMOR, AttributeRuntimeEvents.armorModifier(stack, event.getSlotType(), value));
        });
        AttributeData.sumValue(stack, AttributeType.HEALTH).ifPresent(value -> {
            event.removeAttribute(Attributes.MAX_HEALTH);
            event.addModifier(Attributes.MAX_HEALTH, AttributeRuntimeEvents.healthModifier(stack, event.getSlotType(), value));
        });
        AttributeData.sumValue(stack, AttributeType.ARMOR_TOUGHNESS).ifPresent(value -> {
            event.removeAttribute(Attributes.ARMOR_TOUGHNESS);
            event.addModifier(Attributes.ARMOR_TOUGHNESS,
                    AttributeRuntimeEvents.slotModifier(stack, event.getSlotType(), "armor_toughness", value));
        });
        AttributeData.sumValue(stack, AttributeType.KNOCKBACK_RESISTANCE).ifPresent(value -> {
            event.removeAttribute(Attributes.KNOCKBACK_RESISTANCE);
            event.addModifier(Attributes.KNOCKBACK_RESISTANCE,
                    AttributeRuntimeEvents.slotModifier(stack, event.getSlotType(), "knockback_resistance", value));
        });
    }

    private static void applyWeaponAttributes(ItemStack stack, ItemAttributeModifierEvent event) {
        // "Vanilla attack damage" wins over the plain damage line, which for TACZ guns
        // describes the bullet damage. Both are expressed as a total value.
        OptionalDouble attackDamage = AttributeData.sumValue(stack, AttributeType.ATTACK_DAMAGE);
        if (attackDamage.isEmpty()) {
            attackDamage = AttributeData.sumValue(stack, AttributeType.DAMAGE);
        }
        attackDamage.ifPresent(value -> {
            event.removeAttribute(Attributes.ATTACK_DAMAGE);
            event.addModifier(Attributes.ATTACK_DAMAGE, AttributeRuntimeEvents.weaponDamageModifier(stack, value));
        });
        AttributeData.sumValue(stack, AttributeType.ATTACK_SPEED).ifPresent(value -> {
            event.removeAttribute(Attributes.ATTACK_SPEED);
            event.addModifier(Attributes.ATTACK_SPEED, AttributeRuntimeEvents.attackSpeedModifier(stack, value));
        });
    }

    @SubscribeEvent
    public static void onTaczGunDamage(EntityHurtByGunEvent.Pre event) {
        CompoundTag bulletRuntime = event.getBullet().getPersistentData().getCompound("TaczAttributeData");
        if (!bulletRuntime.isEmpty()) {
            AttributeRuntimeEvents.applyGunDamage(event, bulletRuntime, event.getHurtEntity());
            return;
        }
        LivingEntity attacker = event.getAttacker();
        if (attacker == null) {
            return;
        }
        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof IGun) || !AttributeData.hasData(stack)) {
            return;
        }
        AttributeRuntimeEvents.applyGunDamage(event, stack, event.getHurtEntity());
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        Entity entity = event.getSource().getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity attacker = (LivingEntity)entity;
        ItemStack stack = attacker.getMainHandItem();
        if (stack.isEmpty() || AttributeData.hasData(stack) && stack.getItem() instanceof IGun) {
            return;
        }
        if (stack.isEmpty() || !AttributeData.hasData(stack)) {
            return;
        }
        if (stack.getItem() instanceof IGun) {
            return;
        }
        OptionalDouble damageOverride = AttributeData.sumValue(stack, AttributeType.DAMAGE);
        OptionalDouble pveExtra = AttributeData.sumValue(stack, AttributeType.PVE_DAMAGE);
        if (damageOverride.isEmpty() && pveExtra.isEmpty()) {
            return;
        }
        double damage = damageOverride.orElse(event.getAmount());
        if (!(event.getEntity() instanceof Player)) {
            damage += pveExtra.orElse(0.0);
        }
        event.setAmount((float)damage);
        double rawDamage = damage;
        AttributeData.sumValue(stack, AttributeType.ARMOR_PENETRATION).ifPresent(value -> {
            double penetration = Math.max(0.0, Math.min(1.0, value));
            if (penetration > 0.0) {
                ARMOR_BYPASS.put(event.getEntity(), new ArmorBypassState((float)rawDamage, penetration));
            }
        });
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        ArmorBypassState state = ARMOR_BYPASS.remove(event.getEntity());
        if (state == null) {
            return;
        }
        float finalDamage = event.getAmount();
        event.setAmount((float)((double)finalDamage * (1.0 - state.penetration()) + (double)state.rawDamage() * state.penetration()));
    }

    @SubscribeEvent
    public static void onArmorPveReduction(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Player) {
            return;
        }
        LivingEntity living = event.getEntity();
        double reduction = AttributeData.sumWorn(living, AttributeType.PVE_DEFENSE_REDUCTION);
        if ((reduction = Math.max(0.0, Math.min(1.0, reduction))) > 0.0) {
            event.setAmount((float)((double)event.getAmount() * (1.0 - reduction)));
        }
    }

    private static void applyGunDamage(EntityHurtByGunEvent.Pre event, ItemStack stack, Entity target) {
        double base = AttributeData.sumValue(stack, AttributeType.DAMAGE).orElse(event.getBaseAmount());
        if (target != null && !(target instanceof Player)) {
            base += AttributeData.sumValue(stack, AttributeType.PVE_DAMAGE).orElse(0.0);
        }
        event.setBaseAmount((float)base);
        AttributeData.sumValue(stack, AttributeType.HEADSHOT_BONUS).ifPresent(value -> event.setHeadshotMultiplier((float)value));
    }

    private static void applyGunDamage(EntityHurtByGunEvent.Pre event, CompoundTag runtime, Entity target) {
        double base;
        double d = base = runtime.contains("Damage", 99) ? runtime.getDouble("Damage") : (double)event.getBaseAmount();
        if (target != null && !(target instanceof Player) && runtime.contains("PveDamage", 99)) {
            base += runtime.getDouble("PveDamage");
        }
        event.setBaseAmount((float)base);
        if (runtime.contains("Headshot", 99)) {
            event.setHeadshotMultiplier((float)runtime.getDouble("Headshot"));
        }
    }

    private static AttributeModifier armorModifier(ItemStack stack, EquipmentSlot slot, double value) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((stack.getItem().toString() + slot.name() + "armor").getBytes()), "tacz_attribute_data.armor", value, AttributeModifier.Operation.ADDITION);
    }

    private static AttributeModifier healthModifier(ItemStack stack, EquipmentSlot slot, double value) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((stack.getItem().toString() + slot.name() + "health").getBytes()), "tacz_attribute_data.health", value, AttributeModifier.Operation.ADDITION);
    }

    private static AttributeModifier weaponDamageModifier(ItemStack stack, double value) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((stack.getItem().toString() + "weapon_damage").getBytes()), "tacz_attribute_data.damage", value - 1.0, AttributeModifier.Operation.ADDITION);
    }

    /**
     * The player's base attack speed is 4.0, so a total of {@code value} becomes a
     * {@code value - 4.0} modifier. Weapons normally carry a negative modifier here.
     */
    private static AttributeModifier attackSpeedModifier(ItemStack stack, double value) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((stack.getItem().toString() + "attack_speed").getBytes()), "tacz_attribute_data.attack_speed", value - 4.0, AttributeModifier.Operation.ADDITION);
    }

    /** Generic slot-scoped modifier for the plain additive attributes. */
    private static AttributeModifier slotModifier(ItemStack stack, EquipmentSlot slot, String name, double value) {
        return new AttributeModifier(UUID.nameUUIDFromBytes((stack.getItem().toString() + slot.name() + name).getBytes()), "tacz_attribute_data." + name, value, AttributeModifier.Operation.ADDITION);
    }

    private record ArmorBypassState(float rawDamage, double penetration) {
    }
}

