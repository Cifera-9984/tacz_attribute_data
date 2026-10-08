package com.core.attribute.tacz.compat;

import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.data.AttributeType;
import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shared timing rescale used by the LesRaisins melee mixins.
 *
 * <p>This class deliberately lives <b>outside</b> the mixin packages. Mixin reserves every
 * package declared in a mixin config, and directly loading a class from such a package
 * throws {@code IllegalClassLoadError} at runtime - which crashed the client the moment a
 * melee swing called into it.</p>
 */
public final class LrTiming {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Item ids already reported, so the log stays useful instead of spamming. */
    private static final Set<String> REPORTED = Collections.synchronizedSet(new HashSet<>());

    private LrTiming() {
    }

    /**
     * Rescales whatever the weapon reported. When the weapon has an attack-speed line but
     * its own value is unusable, a one-off line is logged so the reason is visible
     * without a debugger.
     */
    public static void rescale(ItemStack stack, CallbackInfoReturnable<Integer> cir, String which) {
        Integer boxed = cir.getReturnValue();
        int base = boxed == null ? 0 : boxed;
        int scaled = AttributeData.scaledMeleeCooldown(stack, base);
        if (scaled > 0) {
            cir.setReturnValue(scaled);
            return;
        }
        String id = stack.getItem().toString();
        if (AttributeData.hasLine(stack, AttributeType.ATTACK_SPEED) && REPORTED.add(id + ':' + which)) {
            LOGGER.info("[tacz_attribute_data] LR melee {}: attack-speed line present but the weapon's own "
                    + "{} is {} - left unchanged (nothing to scale).", id, which, base);
        }
    }
}
