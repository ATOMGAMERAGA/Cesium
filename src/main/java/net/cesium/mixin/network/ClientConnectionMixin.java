package net.cesium.mixin.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import net.cesium.CesiumMod;
import net.cesium.netty.handler.*;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@link ClientConnection} to:
 * <ul>
 *   <li>Apply TCP socket optimizations when the channel becomes active.</li>
 *   <li>Replace vanilla VarInt codec handlers with Cesium's fast alternatives.</li>
 *   <li>Inject the flush consolidation handler.</li>
 * </ul>
 */
@Mixin(ClientConnection.class)
public abstract class ClientConnectionMixin {

    @Shadow
    private Channel channel;

    /**
     * Called when the channel is fully established. This is the earliest safe
     * point to modify the pipeline after Minecraft has already configured it.
     */
    @Inject(method = "channelActive", at = @At("TAIL"))
    private void cesium$onChannelActive(io.netty.channel.ChannelHandlerContext ctx, CallbackInfo ci) {
        try {
            var config = CesiumMod.getInstance().getConfig();
            if (config == null) return;

            Channel ch = ctx.channel();
            ChannelPipeline pipeline = ch.pipeline();

            // Apply TCP socket options.
            CesiumChannelInitializer.configure(ch);

            // Replace "splitter" with optimized VarInt frame decoder.
            if (pipeline.get("splitter") != null) {
                pipeline.replace("splitter", "splitter", new CesiumVarIntFrameDecoder());
            }

            // Replace "prepender" with optimized VarInt frame encoder.
            if (pipeline.get("prepender") != null) {
                pipeline.replace("prepender", "prepender", new CesiumVarIntFrameEncoder());
            }

            // Insert flush consolidation handler before the packet encoder.
            if (config.isFlushConsolidationEnabled()
                    && pipeline.get("cesium_flush") == null
                    && pipeline.get("encoder") != null) {
                pipeline.addBefore("encoder", "cesium_flush",
                        new CesiumFlushConsolidationHandler(config.getExplicitFlushAfterFlushes()));
            }

            // Notify ConnectionStabilizer.
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine != null && engine.getConnectionStabilizer() != null) {
                engine.getConnectionStabilizer().onChannelActive(ch);
            }

            CesiumMod.LOGGER.debug("[Cesium] Pipeline configured for {}", ch.remoteAddress());

        } catch (Exception e) {
            CesiumMod.LOGGER.warn("[Cesium] Failed to configure pipeline: {}", e.getMessage());
        }
    }

    /**
     * Called when the channel is closed / disconnected.
     */
    @Inject(method = "channelInactive", at = @At("HEAD"))
    private void cesium$onChannelInactive(io.netty.channel.ChannelHandlerContext ctx, CallbackInfo ci) {
        CesiumMod.LOGGER.debug("[Cesium] Channel disconnected.");
    }
}
