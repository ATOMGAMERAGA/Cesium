package net.cesium.mixin.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.cesium.CesiumMod;
import net.cesium.netty.handler.*;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla compression handlers with Cesium's adaptive variants
 * whenever compression is enabled on the connection.
 *
 * <p>Handler replacement:
 * <pre>
 *   "decompress" (PacketInflater)  → CesiumPacketDecompressor
 *   "compress"   (PacketDeflater)  → CesiumPacketCompressor
 * </pre>
 *
 * <p>VarInt codec replacement and flush consolidation happen in
 * {@link ClientConnectionMixin#cesium$onChannelActive} because that fires
 * earlier (right when the channel connects).
 */
@Mixin(ClientConnection.class)
public abstract class ChannelInitializerMixin {

    /**
     * Invoked after {@code setCompressionThreshold} reconfigures the pipeline.
     * We replace the vanilla compressor/decompressor with our adaptive versions.
     */
    @Inject(method = "setCompressionThreshold", at = @At("TAIL"))
    private void cesium$afterSetCompression(int threshold, boolean rejectBadPackets, CallbackInfo ci) {
        try {
            Channel channel = ((ClientConnectionAccessor) this).cesium$getChannel();
            if (channel == null) return;
            ChannelPipeline pipeline = channel.pipeline();

            if (pipeline.get("decompress") != null) {
                pipeline.replace("decompress", "decompress",
                        new CesiumPacketDecompressor(threshold));
            }
            if (pipeline.get("compress") != null) {
                pipeline.replace("compress", "compress",
                        new CesiumPacketCompressor(threshold));
            }

            CesiumMod.LOGGER.debug("[Cesium] Replaced compression handlers (threshold={})", threshold);
        } catch (Exception e) {
            CesiumMod.LOGGER.warn("[Cesium] Failed to replace compression handlers: {}", e.getMessage());
        }
    }
}
