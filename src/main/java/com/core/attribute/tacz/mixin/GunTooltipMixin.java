package com.core.attribute.tacz.mixin;

import com.core.attribute.tacz.data.GunOverrides;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.inventory.tooltip.GunTooltip;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the TACZ gun panel show the gun's overridden main ammo.
 *
 * <p>The ammo name and icon in the panel come from the {@code GunTooltip}'s ammo id, which
 * TACZ fills from the gun type's shared {@code GunData} - so it cannot reflect a per-item
 * override. Swapping the id here means the client-side tooltip renderer builds the correct
 * ammo stack, name and magazine line for the item.</p>
 */
@Mixin(value = GunTooltip.class, remap = false)
public class GunTooltipMixin {

    @Mutable
    @Final
    @Shadow
    private ResourceLocation ammoId;

    @Inject(
            method = "<init>(Lnet/minecraft/world/item/ItemStack;Lcom/tacz/guns/api/item/IGun;Lnet/minecraft/resources/ResourceLocation;Lcom/tacz/guns/resource/index/CommonGunIndex;)V",
            at = {@At(value = "TAIL")}, remap = false, require = 0)
    private void tacz_attribute_data$overrideAmmo(ItemStack gun, IGun iGun, ResourceLocation ammoId,
                                                  CommonGunIndex gunIndex, CallbackInfo ci) {
        ResourceLocation override = GunOverrides.ammoId(gun);
        if (override != null) {
            this.ammoId = override;
        }
    }
}
