package net.cesium.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.cesium.CesiumMod;
import net.cesium.monitoring.NetworkStatistics;
import net.minecraft.network.handler.DecoderHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hooks into the packet decoding path to record inbound traffic statistics.
 * Target: {@link DecoderHandler} (net.minecraft.network.handler.DecoderHandler)
 */
@Mixin(DecoderHandler.class)
public abstract class PacketDecoderMixin {

    @Inject(method = "decode", at = @At("HEAD"))
    private void cesium$beforeDecode(ChannelHandlerContext ctx, ByteBuf buf,
                                     List<Object> out, CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;
            NetworkStatistics stats = engine.getNetworkStatistics();
            if (stats != null) {
                stats.recordBytesReceived(buf.readableBytes());
            }
        } catch (Exception ignored) {}
    }
}
