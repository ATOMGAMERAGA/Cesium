package net.cesium.mixin.client;

import net.cesium.CesiumMod;
import net.cesium.monitoring.NetworkStatistics;
import net.minecraft.client.MinecraftClient;
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
 * <p>RTT measurement strategy: the server sends a KeepAlive packet and
 * records the RTT when the client's response arrives. The server then
 * broadcasts the measured latency back to clients via the player-list update
 * packet. We read this server-measured value from the local player's
 * {@link net.minecraft.client.network.PlayerListEntry} on every KeepAlive
 * cycle, giving us a reliable per-cycle RTT sample without needing to hook
 * low-level transport timers.
 *
 * <p>KeepAlive is handled in the common superclass (1.21.4+), not the play
 * handler, so this mixin correctly targets {@link ClientCommonNetworkHandler}.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void cesium$onKeepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;

            NetworkStatistics stats = engine.getNetworkStatistics();
            if (stats == null) return;

            // Read the server-measured RTT from the local player's tab-list entry.
            // The server sends PlayerListS2CPacket periodically with updated latencies;
            // we sample it here (on each KeepAlive) as a convenient periodic trigger.
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                var networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    var entry = networkHandler.getPlayerListEntry(mc.player.getUuid());
                    if (entry != null) {
                        int latencyMs = entry.getLatency();
                        if (latencyMs > 0) {
                            stats.recordRtt(latencyMs);
                            engine.getLatencyCompensator().update();

                            // Also feed jitter buffer with the latest RTT sample.
                            var jitterBuffer = engine.getJitterBuffer();
                            if (jitterBuffer != null) {
                                jitterBuffer.updateJitter(latencyMs);
                            }

                            CesiumMod.LOGGER.debug(
                                    "[Cesium] KeepAlive id={} | RTT={}ms", packet.getId(), latencyMs);
                        }
                    }
                }
            }
        } catch (Exception e) {
            CesiumMod.LOGGER.debug("[Cesium] KeepAlive hook error: {}", e.getMessage());
        }
    }
}
