package com.core.attribute.tacz.mixin;

import com.core.attribute.tacz.data.AttributeData;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={AbstractGunItem.class}, remap=false)
public class AbstractGunItemMixin {
    /**
     * TACZ ships as a production (SRG-named) jar, so the vanilla method it overrides is
     * named {@code m_142422_} at runtime; in a mojmap dev workspace it is
     * {@code getTooltipImage}. Both candidates are listed (remap=false) so the injection
     * resolves in either environment. require=0 keeps it a no-op if neither matches.
     */
    @Inject(method={"m_142422_(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;", "getTooltipImage(Lnet/minecraft/world/item/ItemStack;)Ljava/util/Optional;"}, at={@At(value="HEAD")}, cancellable=true, require=0, remap=false)
    private void tacz_attribute_data$getTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (!AttributeData.hasData(stack)) {
            return;
        }
        boolean hideOriginalPanel = AttributeData.hideVanillaPanel(stack);
        AttributeData.applyTooltipHideFlags(stack, hideOriginalPanel);
        if (!hideOriginalPanel) {
            return;
        }
        cir.setReturnValue(Optional.empty());
    }
}

