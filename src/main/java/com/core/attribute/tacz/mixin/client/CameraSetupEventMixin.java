package com.core.attribute.tacz.mixin.client;

import com.core.attribute.tacz.data.GunOverrides;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Scales the client-side camera recoil by the gun's per-item multiplier.
 *
 * <p>TACZ only ever applies recoil on the client. The multiplier that reaches
 * {@code GunRecoil#genPitchSplineFunction}/{@code genYawSplineFunction} is the value the
 * spline is scaled by every frame, so adjusting that single argument rescales the whole
 * kick without touching the gun's shared {@code GunRecoil} data (which is global per gun
 * type, not per item). The multiplier is read from the locally held gun, which is also
 * the gun that was just fired.</p>
 */
@Mixin(value = CameraSetupEvent.class, remap = false)
public class CameraSetupEventMixin {

    @ModifyArg(
            method = "initialCameraRecoil(Lcom/tacz/guns/api/event/common/GunFireEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genPitchSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"),
            index = 0, remap = false, require = 0)
    private static float tacz_attribute_data$scalePitch(float multiplier) {
        return tacz_attribute_data$scale(multiplier);
    }

    @ModifyArg(
            method = "initialCameraRecoil(Lcom/tacz/guns/api/event/common/GunFireEvent;)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/tacz/guns/resource/pojo/data/gun/GunRecoil;genYawSplineFunction(F)Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;"),
            index = 0, remap = false, require = 0)
    private static float tacz_attribute_data$scaleYaw(float multiplier) {
        return tacz_attribute_data$scale(multiplier);
    }

    private static float tacz_attribute_data$scale(float multiplier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return multiplier;
        }
        double factor = GunOverrides.recoilMultiplier(minecraft.player.getMainHandItem());
        return factor == GunOverrides.RECOIL_DEFAULT ? multiplier : (float)(multiplier * factor);
    }
}
