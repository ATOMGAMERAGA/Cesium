package net.cesium.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import net.cesium.CesiumMod;
import net.cesium.packet.AdaptiveCompressionStrategy;
import net.cesium.monitoring.NetworkStatistics;

import java.util.zip.Deflater;

/**
 * Replaces Minecraft's {@code PacketDeflater} with an adaptive multi-algorithm
 * compressor.
 *
 * <p>Compression decision (per packet):
 * <ol>
 *   <li>If {@code size < threshold}: pass through unchanged (cost: 0).</li>
 *   <li>Otherwise: delegate to {@link AdaptiveCompressionStrategy} which
 *       selects between LZ4, Zstandard, and zlib based on packet size.</li>
 *   <li>If compressed output is larger than input: send uncompressed.</li>
 * </ol>
 *
 * <p>Wire format (matches Minecraft's protocol):
 * <pre>
 *   VarInt  dataLength   (0 = not compressed, >0 = original size)
 *   bytes   payload
 * </pre>
 */
public class CesiumPacketCompressor extends MessageToByteEncoder<ByteBuf> {

    private final int compressionThreshold;
    private final AdaptiveCompressionStrategy strategy;
    private final Deflater deflater;

    public CesiumPacketCompressor(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
        this.strategy = new AdaptiveCompressionStrategy();
        this.deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int originalLength = msg.readableBytes();

        if (originalLength < compressionThreshold) {
            // Packet too small to compress — send as-is.
            CesiumVarIntFrameEncoder.writeVarInt(out, 0); // dataLength = 0 → not compressed
            out.writeBytes(msg);
            return;
        }

        // Attempt compression using the adaptive strategy.
        byte[] inputBytes = new byte[originalLength];
        msg.readBytes(inputBytes);

        byte[] compressed = strategy.compress(inputBytes, deflater);

        if (compressed != null && compressed.length < originalLength) {
            // Compression saved space.
            CesiumVarIntFrameEncoder.writeVarInt(out, originalLength);
            out.writeBytes(compressed);
            recordStats(originalLength, compressed.length);
        } else {
            // Compression made it larger (or failed) — send raw.
            CesiumVarIntFrameEncoder.writeVarInt(out, 0);
            out.writeBytes(inputBytes);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        deflater.end();
        super.handlerRemoved(ctx);
    }

    private void recordStats(int original, int compressed) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine != null) {
                NetworkStatistics stats = engine.getNetworkStatistics();
                if (stats != null) {
                    stats.recordCompression(original, compressed);
                }
            }
        } catch (Exception ignored) {
            // Statistics are optional — never let them crash the network path.
        }
    }
}
