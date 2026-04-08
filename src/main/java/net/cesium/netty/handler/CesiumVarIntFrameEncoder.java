package net.cesium.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Optimized VarInt-length-prefix frame encoder replacing Minecraft's
 * vanilla {@code Prepender}.
 *
 * <p>Writes the VarInt header inline with the payload in a single pass,
 * avoiding any intermediate buffer allocation.
 */
@ChannelHandler.Sharable
public class CesiumVarIntFrameEncoder extends MessageToByteEncoder<ByteBuf> {

    /** Maximum header bytes: 3 (covers all valid Minecraft packet lengths). */
    private static final int HEADER_SIZE = 3;

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int bodyLength = msg.readableBytes();
        int headerLength = varIntSize(bodyLength);

        out.ensureWritable(headerLength + bodyLength);
        writeVarInt(out, bodyLength);
        out.writeBytes(msg);
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg, boolean preferDirect) {
        // Allocate a buffer large enough for header + body in one go.
        int bodyLength = msg.readableBytes();
        int totalLength = HEADER_SIZE + bodyLength;
        return preferDirect
                ? ctx.alloc().directBuffer(totalLength)
                : ctx.alloc().heapBuffer(totalLength);
    }

    /**
     * Returns the number of bytes required to encode {@code value} as a VarInt.
     */
    public static int varIntSize(int value) {
        if ((value & ~0x7F) == 0)        return 1;
        if ((value & ~0x3FFF) == 0)      return 2;
        if ((value & ~0x1FFFFF) == 0)    return 3;
        // Minecraft packets never exceed 2 MB, so 4- and 5-byte cases are unreachable.
        return 4;
    }

    /**
     * Writes {@code value} as a VarInt into {@code buf}.
     */
    public static void writeVarInt(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                buf.writeByte(value);
                return;
            }
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }
}
