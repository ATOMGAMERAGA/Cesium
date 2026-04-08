package net.cesium.mixin.client;

import net.cesium.CesiumMod;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts KeepAlive handling in {@link ClientCommonNetworkHandler} to
 * measure precise round-trip times for the Cesium network statistics.
 *
 * <p>KeepAlive is handled in the common superclass (1.21.4), not the play handler.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void cesium$onKeepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;
            // Record send timestamp so we can measure RTT when the response is confirmed.
            // For now, we record the keepAlive ID receipt — a full PingTracker
            // integration connecting to the C2S KeepAlive response is in Phase 5.
            CesiumMod.LOGGER.debug("[Cesium] KeepAlive received: id={}", packet.getId());
        } catch (Exception e) {
            CesiumMod.LOGGER.debug("[Cesium] KeepAlive hook: {}", e.getMessage());
        }
    }
}
