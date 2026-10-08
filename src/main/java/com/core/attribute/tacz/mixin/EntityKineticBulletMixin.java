package com.core.attribute.tacz.mixin;

import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.data.AttributeType;
import com.core.attribute.tacz.data.GunOverrides;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityKineticBullet.class}, remap=false)
public class EntityKineticBulletMixin {
    @Shadow
    private float armorIgnore;
    @Shadow
    private ResourceLocation ammoId;
    @Shadow
    private float speed;
    @Shadow
    private float gravity;
    @Shadow
    private float friction;
    @Shadow
    private float knockback;
    @Shadow
    private int pierce;
    @Shadow
    private int life;

    @Inject(method={"<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V"}, at={@At(value="TAIL")})
    private void tacz_attribute_data$overrideArmorPenetration(EntityType<? extends Projectile> type, Level worldIn, LivingEntity throwerIn, ItemStack gunItem, ResourceLocation ammoId, ResourceLocation gunId, ResourceLocation gunDisplayId, boolean isTracerAmmo, GunData gunData, BulletData bulletData, CallbackInfo ci) {
        AttributeData.sumValue(gunItem, AttributeType.ARMOR_PENETRATION).ifPresent(value -> {
            double clamped = Mth.clamp((double)value, (double)0.0, (double)1.0);
            this.armorIgnore = (float)clamped;
            this.runtimeTag().putDouble("ArmorPenetration", clamped);
        });
        AttributeData.sumValue(gunItem, AttributeType.DAMAGE).ifPresent(value -> this.runtimeTag().putDouble("Damage", value));
        AttributeData.sumValue(gunItem, AttributeType.PVE_DAMAGE).ifPresent(value -> this.runtimeTag().putDouble("PveDamage", value));
        AttributeData.sumValue(gunItem, AttributeType.HEADSHOT_BONUS).ifPresent(value -> this.runtimeTag().putDouble("Headshot", value));

        // ---- gun card overrides: main ammo + ballistic profile ----
        // The entity is built (server side) before it is synced, so overwriting the fields
        // here also changes what writeSpawnData sends to nearby clients.
        ResourceLocation overrideAmmo = GunOverrides.ammoId(gunItem);
        if (overrideAmmo != null) {
            this.ammoId = overrideAmmo;
        }
        GunOverrides.bullet(gunItem, GunOverrides.BULLET_SPEED).ifPresent(value -> this.speed = (float)value);
        GunOverrides.bullet(gunItem, GunOverrides.BULLET_GRAVITY).ifPresent(value -> this.gravity = (float)value);
        GunOverrides.bullet(gunItem, GunOverrides.BULLET_KNOCKBACK).ifPresent(value -> this.knockback = (float)value);
        GunOverrides.bullet(gunItem, GunOverrides.BULLET_PIERCE).ifPresent(value -> this.pierce = (int)Math.round(value));
        GunOverrides.bullet(gunItem, GunOverrides.BULLET_LIFE).ifPresent(value -> this.life = Mth.clamp((int)Math.round(value * 20.0), 1, Integer.MAX_VALUE));
    }

    private CompoundTag runtimeTag() {
        CompoundTag persistent = ((Entity)(Object)this).getPersistentData();
        if (!persistent.contains("TaczAttributeData")) {
            persistent.put("TaczAttributeData", (Tag)new CompoundTag());
        }
        return persistent.getCompound("TaczAttributeData");
    }
}
