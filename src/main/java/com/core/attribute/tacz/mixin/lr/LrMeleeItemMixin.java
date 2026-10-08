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
 * LesRaisins Tactical Equipments compatibility - <b>current signature</b> (LR &gt;= 0.4.x).
 *
 * <p>LR melee weapons gate their swings through their own timing values, read straight
 * from the weapon index, instead of the vanilla attack-speed attribute (LR never
 * references {@code Attributes.ATTACK_SPEED}). LR consumes both in
 * {@code CombatProperties.preAttack}, so both are hooked.</p>
 *
 * <p>In 0.4.x these methods gained a trailing {@code int} parameter; the handler signature
 * must match the target exactly or Mixin refuses the injection. See
 * {@link LrMeleeItemLegacyMixin} for the 0.3.x shape - whichever class does not match the
 * installed LR only produces one harmless startup warning.</p>
 *
 * <p>The attack-speed line acts as a speed multiplier: {@code 1.0} keeps the weapon's
 * original timing, {@code 2.0} halves it, {@code 0.5} doubles it, so each weapon keeps its
 * own base feel.</p>
 *
 * <p>This mixin lives in its own config declared {@code required = false}, so with LR
 * absent the config is skipped and nothing else is affected.</p>
 */
@Mixin(value = MeleeItem.class, remap = false)
public class LrMeleeItemMixin {

    @Inject(method = {"getAttackCoolDown"}, at = {@At(value = "RETURN")},
            cancellable = true, require = 0, remap = false)
    private void tacz_attribute_data$attackCoolDown(ItemStack stack, MeleeAction action, int index,
                                                  CallbackInfoReturnable<Integer> cir) {
        LrTiming.rescale(stack, cir, "cooldown");
    }

    @Inject(method = {"getAttackDelay"}, at = {@At(value = "RETURN")},
            cancellable = true, require = 0, remap = false)
    private void tacz_attribute_data$attackDelay(Player player, ItemStack stack, MeleeAction action, int index,
                                               CallbackInfoReturnable<Integer> cir) {
        LrTiming.rescale(stack, cir, "delay");
    }
}
