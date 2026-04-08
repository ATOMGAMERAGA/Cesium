package net.cesium.mixin.entity;

import net.cesium.CesiumMod;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the local player's tick to update the latency compensator and
 * advance movement prediction corrections.
 *
 * <p>Only fires for the local {@link ClientPlayerEntity}; all other
 * PlayerEntity instances (remote players, NPCs) are skipped.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void cesium$onTick(CallbackInfo ci) {
        if (!((Object) this instanceof ClientPlayerEntity)) return;
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;

            // Refresh smoothed RTT and recompute recommended interpolation delay.
            engine.getLatencyCompensator().update();

            // Advance the smooth position-correction step (if one is active).
            var predictor = engine.getMovementPredictor();
            if (predictor != null && predictor.isCorrectingPosition()) {
                double[] delta = predictor.tickCorrection();
                double magnitude = Math.sqrt(
                        delta[0] * delta[0] + delta[1] * delta[1] + delta[2] * delta[2]);
                if (magnitude > 0.001) {
                    CesiumMod.LOGGER.debug(
                            "[Cesium] MovementPredictor correction this tick: %.4f blocks", magnitude);
                }
            }
        } catch (Exception ignored) {}
    }
}
