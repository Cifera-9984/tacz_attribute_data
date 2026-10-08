package com.core.attribute.tacz.mixin;

import com.core.attribute.tacz.data.AttributeData;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces the enchantment glint on items flagged by the editor.
 *
 * <p>1.20.1 has no vanilla "glint override" data, so the only way to make an
 * unenchanted item shimmer is to intercept the foil check. Both the production (SRG)
 * and the development (mojmap) names are listed with {@code remap=false}, matching the
 * convention used by the other mixins in this mod: the build ships an empty refmap, so
 * names must be written out explicitly.</p>
 */
@Mixin(value = {ItemStack.class}, remap = false)
public class ItemStackMixin {

    @Inject(method = {"m_41790_()Z", "hasFoil()Z"}, at = {@At(value = "HEAD")},
            cancellable = true, require = 0, remap = false)
    private void tacz_attribute_data$forceGlint(CallbackInfoReturnable<Boolean> cir) {
        if (AttributeData.hasGlint((ItemStack) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
