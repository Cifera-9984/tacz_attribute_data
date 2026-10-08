package com.core.attribute.tacz.mixin.lr;

import com.core.attribute.tacz.compat.LrTiming;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.item.MeleeItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LesRaisins Tactical Equipments compatibility - <b>legacy signature</b> (LR 0.3.x), which
 * declares these methods without the trailing {@code int} parameter that 0.4.x added.
 *
 * <p>Mixin requires the handler signature to match the target exactly, so both shapes must
 * be declared separately; the one that does not match the installed LR is rejected with a
 * single startup warning (the game carries on normally). This keeps the mod working both
 * on a 0.4.x client and against a server still running 0.3.x.</p>
 */
@Mixin(value = MeleeItem.class, remap = false)
public class LrMeleeItemLegacyMixin {

    @Inject(method = {"getAttackCoolDown"}, at = {@At(value = "RETURN")},
            cancellable = true, require = 0, remap = false)
    private void tacz_attribute_data$attackCoolDownLegacy(ItemStack stack, MeleeAction action,
                                                        CallbackInfoReturnable<Integer> cir) {
        LrTiming.rescale(stack, cir, "cooldown");
    }

    @Inject(method = {"getAttackDelay"}, at = {@At(value = "RETURN")},
            cancellable = true, require = 0, remap = false)
    private void tacz_attribute_data$attackDelayLegacy(Player player, ItemStack stack, MeleeAction action,
                                                     CallbackInfoReturnable<Integer> cir) {
        LrTiming.rescale(stack, cir, "delay");
    }
}
