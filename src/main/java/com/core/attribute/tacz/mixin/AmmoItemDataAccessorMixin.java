package com.core.attribute.tacz.mixin;

import com.core.attribute.tacz.data.GunOverrides;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a gun accept an ammo type other than the one baked into its {@code GunData}.
 *
 * <p>TACZ decides what a gun can load by comparing the ammo's id against
 * {@code GunData#getAmmoId()} through this default method, which is reached from both the
 * reload path and the inventory ammo check. Overriding it for the gun's own override is
 * therefore enough to reroute the whole ammo economy, on both sides.</p>
 *
 * <p>This is an interface mixin: the handler is a private default method merged into
 * {@link AmmoItemDataAccessor}.</p>
 */
@Mixin(value = AmmoItemDataAccessor.class, remap = false)
public interface AmmoItemDataAccessorMixin {

    @Inject(method = "isAmmoOfGun(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void tacz_attribute_data$isAmmoOfGun(ItemStack gun, ItemStack ammo, CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation override = GunOverrides.ammoId(gun);
        if (override == null) {
            return;
        }
        IAmmo iAmmo = IAmmo.getIAmmoOrNull(ammo);
        if (iAmmo == null) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(override.equals(iAmmo.getAmmoId(ammo)));
    }
}
