package net.cesium.mixin.entity;

import net.cesium.CesiumMod;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Hooks into the client world's tick to synchronise Cesium's interpolation
 * and jitter-buffer systems with the game loop.
 *
 * <p>Injected into {@code ClientWorld.tick(BooleanSupplier)} at its tail so
 * that all entity position updates for the current tick are already committed
 * before we read network statistics and adjust compensation parameters.
 */
@Mixin(ClientWorld.class)
public abstract class EntityTrackerMixin {

    /** Run the full compensation update once per second (every 20 ticks). */
    @Unique
    private int cesium$tickAccumulator = 0;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("TAIL"), require = 0)
    private void cesium$afterTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (++cesium$tickAccumulator < 20) return;
        cesium$tickAccumulator = 0;

        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;

            // Recompute the recommended interpolation delay based on smoothed RTT.
            engine.getLatencyCompensator().update();

            // Feed latest jitter data into the adaptive jitter buffer.
            var stats = engine.getNetworkStatistics();
            var jitterBuffer = engine.getJitterBuffer();
            if (stats != null && jitterBuffer != null) {
                jitterBuffer.updateJitter(stats.getLastRttMs());
            }
        } catch (Exception ignored) {}
    }
}
