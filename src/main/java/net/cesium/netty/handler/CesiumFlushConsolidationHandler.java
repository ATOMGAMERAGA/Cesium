package net.cesium.netty.handler;

import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidates multiple {@link Channel#flush()} calls within the same
 * event-loop iteration into a single system call.
 *
 * <p>Inspired by Netty's own {@code FlushConsolidationHandler} and the
 * port used in Velocity. Key difference: uses a counter-based approach
 * to decide when to force-flush, which works well with Minecraft's
 * bursty packet patterns (many small packets per tick, then silence).
 *
 * <p><b>How it works:</b>
 * <ol>
 *   <li>Every call to {@link #flush} increments an internal counter.</li>
 *   <li>The actual flush is deferred until either:
 *     <ul>
 *       <li>the counter reaches {@code explicitFlushAfterFlushes}, or</li>
 *       <li>the event-loop task queue becomes empty (read-complete).</li>
 *     </ul>
 *   </li>
 *   <li>A {@code ChannelOutboundBuffer} writability change always triggers an
 *       immediate flush to avoid head-of-line blocking.</li>
 * </ol>
 */
@ChannelHandler.Sharable
public class CesiumFlushConsolidationHandler extends ChannelDuplexHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumFlush");

    private final int explicitFlushAfterFlushes;

    /** Number of write calls since last actual flush. */
    private int flushCount = 0;
    private boolean readInProgress = false;
    private boolean needFlush = false;

    public CesiumFlushConsolidationHandler(int explicitFlushAfterFlushes) {
        if (explicitFlushAfterFlushes <= 0) {
            throw new IllegalArgumentException("explicitFlushAfterFlushes must be > 0");
        }
        this.explicitFlushAfterFlushes = explicitFlushAfterFlushes;
    }

    // Default: match the config default (256 pending writes before force-flush)
    public CesiumFlushConsolidationHandler() {
        this(256);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        if (readInProgress) {
            // Batch flushes while reading; they'll be flushed at readComplete.
            needFlush = true;
        } else if (++flushCount >= explicitFlushAfterFlushes) {
            // Reached the consolidation threshold — flush now.
            flushCount = 0;
            needFlush = false;
            ctx.flush();
        } else {
            // Schedule a flush at the end of the current event-loop task.
            needFlush = true;
            scheduleFlush(ctx);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // End of a read burst — flush everything that's been queued.
        if (needFlush) {
            flushCount = 0;
            needFlush = false;
            ctx.flush();
        }
        readInProgress = false;
        ctx.fireChannelReadComplete();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        readInProgress = true;
        ctx.fireChannelRead(msg);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (!ctx.channel().isWritable()) {
            // Channel buffer is full — flush immediately to unblock.
            flushCount = 0;
            needFlush = false;
            ctx.flush();
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        // Flush any pending data before closing.
        if (needFlush) {
            ctx.flush();
        }
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        if (needFlush) {
            ctx.flush();
        }
        ctx.close(promise);
    }

    /**
     * Schedules a flush at the end of the current event-loop iteration via
     * {@link EventLoop#execute}. If the event loop is already busy with tasks
     * from this tick, the flush will still happen within the same "burst window".
     */
    private void scheduleFlush(ChannelHandlerContext ctx) {
        ctx.channel().eventLoop().execute(() -> {
            if (needFlush) {
                flushCount = 0;
                needFlush = false;
                ctx.flush();
            }
        });
    }
}
