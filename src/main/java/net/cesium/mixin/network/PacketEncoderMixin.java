package net.cesium.mixin.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.cesium.CesiumMod;
import net.cesium.monitoring.NetworkStatistics;
import net.minecraft.network.handler.EncoderHandler;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the packet encoding path to record outbound traffic statistics.
 * Target: {@link EncoderHandler} (net.minecraft.network.handler.EncoderHandler)
 */
@Mixin(EncoderHandler.class)
public abstract class PacketEncoderMixin {

    @Inject(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At("TAIL"))
    private void cesium$afterEncode(ChannelHandlerContext ctx, Packet<?> packet,
                                    ByteBuf buf, CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;
            NetworkStatistics stats = engine.getNetworkStatistics();
            if (stats != null) {
                stats.recordBytesSent(buf.readableBytes());
            }
        } catch (Exception ignored) {}
    }
}
