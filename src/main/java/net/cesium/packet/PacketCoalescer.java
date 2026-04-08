package net.cesium.packet;

import net.cesium.CesiumConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Coalesces small outgoing packets that arrive within the same tick into a
 * single batch for transmission.
 *
 * <p>Only packets classified as {@link PacketPriorityQueue.Priority#LOW} or
 * {@link PacketPriorityQueue.Priority#NORMAL} are eligible for coalescing.
 * CRITICAL and HIGH packets always bypass the coalescer and are sent immediately.
 *
 * <p>The coalesce window is governed by {@code coalesceWindowMs} in the config
 * (default 5 ms). At the end of each window all accumulated packets are flushed
 * to the channel in one write batch.
 */
public class PacketCoalescer {

    private final int windowMs;
    private final Deque<Object> pending = new ArrayDeque<>();
    private long windowStartMs = 0;

    public PacketCoalescer(CesiumConfig config) {
        this.windowMs = config.getCoalesceWindowMs();
    }

    /**
     * Submits a packet for potential coalescing.
     *
     * @param packet    the packet object
     * @param priority  its classified priority
     * @param sender    callback invoked for each packet that should be sent now
     * @return {@code true} if the packet was buffered, {@code false} if sent immediately
     */
    public boolean submit(Object packet, PacketPriorityQueue.Priority priority,
                          Consumer<Object> sender) {
        if (priority == PacketPriorityQueue.Priority.CRITICAL
                || priority == PacketPriorityQueue.Priority.HIGH) {
            sender.accept(packet);
            return false;
        }

        long now = System.currentTimeMillis();
        if (windowStartMs == 0) {
            windowStartMs = now;
        }

        pending.addLast(packet);

        if (now - windowStartMs >= windowMs) {
            flush(sender);
        }
        return true;
    }

    /**
     * Forces all pending packets through the sender. Call at end-of-tick.
     */
    public void flush(Consumer<Object> sender) {
        while (!pending.isEmpty()) {
            sender.accept(pending.pollFirst());
        }
        windowStartMs = 0;
    }

    public int pendingCount() { return pending.size(); }

    public void clear() {
        pending.clear();
        windowStartMs = 0;
    }
}
