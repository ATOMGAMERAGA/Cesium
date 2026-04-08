package net.cesium.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;

import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Replaces Minecraft's {@code PacketInflater} with a decompressor that
 * includes decompression-bomb protection and avoids unnecessary allocations
 * when packets arrive uncompressed.
 */
public class CesiumPacketDecompressor extends ByteToMessageDecoder {

    /** Maximum allowed decompressed packet size (8 MB safety cap). */
    private static final int MAX_DECOMPRESSED_SIZE = 8 * 1024 * 1024;

    private final int compressionThreshold;
    private final Inflater inflater;

    public CesiumPacketDecompressor(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
        this.inflater = new Inflater(true);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) return;

        int[] varIntResult = CesiumVarIntFrameDecoder.readVarInt(in);
        if (varIntResult == null) return;

        int dataLength = varIntResult[0]; // 0 = not compressed

        if (dataLength == 0) {
            // Packet was not compressed — forward as-is.
            out.add(in.readRetainedSlice(in.readableBytes()));
            return;
        }

        // ── Decompression-bomb protection ──────────────────────────────────
        if (dataLength < compressionThreshold) {
            throw new DecoderException(
                    "Badly compressed packet: claimed size " + dataLength
                            + " is below threshold " + compressionThreshold);
        }
        if (dataLength > MAX_DECOMPRESSED_SIZE) {
            throw new DecoderException(
                    "Badly compressed packet: claimed size " + dataLength
                            + " exceeds max " + MAX_DECOMPRESSED_SIZE);
        }

        // ── Decompress ─────────────────────────────────────────────────────
        byte[] compressedBytes = new byte[in.readableBytes()];
        in.readBytes(compressedBytes);

        inflater.setInput(compressedBytes);
        byte[] decompressed = new byte[dataLength];
        try {
            int written = inflater.inflate(decompressed);
            if (written != dataLength) {
                throw new DecoderException(
                        "Decompressed length mismatch: expected " + dataLength + ", got " + written);
            }
        } catch (DataFormatException e) {
            throw new DecoderException("Failed to decompress packet", e);
        } finally {
            inflater.reset();
        }

        out.add(ctx.alloc().buffer(dataLength).writeBytes(decompressed));
    }

    @Override
    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        inflater.end();
    }
}
