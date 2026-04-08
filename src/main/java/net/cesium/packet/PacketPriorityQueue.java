package net.cesium.packet;

import java.util.concurrent.PriorityBlockingQueue;

/**
 * Priority queue for outgoing Minecraft packets.
 *
 * <p>Priority tiers (lower ordinal = higher priority):
 * <pre>
 *   CRITICAL  — KeepAlive, PlayerPosition, PlayerAction, UseItem
 *   HIGH      — BlockChange, ChatMessage, EntityAction
 *   NORMAL    — ChunkData, EntityMetadata, Inventory
 *   LOW       — Statistics, Advancements, TabCompletion
 * </pre>
 *
 * CRITICAL packets are never queued — they bypass the priority queue and
 * are sent immediately via the direct channel write path in the mixin.
 */
public class PacketPriorityQueue {

    public enum Priority {
        CRITICAL(0),
        HIGH(1),
        NORMAL(2),
        LOW(3);

        public final int value;

        Priority(int value) {
            this.value = value;
        }
    }

    /** A packet entry in the queue, ordered by priority then insertion order. */
    public record QueuedPacket(Object packet, Priority priority, long sequence)
            implements Comparable<QueuedPacket> {

        @Override
        public int compareTo(QueuedPacket other) {
            int cmp = Integer.compare(this.priority.value, other.priority.value);
            if (cmp != 0) return cmp;
            return Long.compare(this.sequence, other.sequence); // FIFO within same priority
        }
    }

    private final PriorityBlockingQueue<QueuedPacket> queue = new PriorityBlockingQueue<>();
    private long sequenceCounter = 0;

    public void enqueue(Object packet, Priority priority) {
        queue.offer(new QueuedPacket(packet, priority, sequenceCounter++));
    }

    public QueuedPacket poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public void clear() {
        queue.clear();
    }

    /**
     * Classifies a packet class name into a priority tier.
     *
     * <p>Uses simple string matching against Minecraft's obfuscated or Yarn-mapped
     * class names. This is intentionally conservative — unrecognized packets default
     * to NORMAL.
     */
    public static Priority classify(String packetClassName) {
        if (packetClassName == null) return Priority.NORMAL;
        String name = packetClassName.toLowerCase();

        // CRITICAL: latency-sensitive control packets
        if (name.contains("keepalive")
                || name.contains("playerposition")
                || name.contains("playermove")
                || name.contains("playeraction")
                || name.contains("useitem")
                || name.contains("handswing")) {
            return Priority.CRITICAL;
        }

        // HIGH: gameplay-impactful but not immediately latency-critical
        if (name.contains("blockchange")
                || name.contains("chat")
                || name.contains("message")
                || name.contains("entityaction")
                || name.contains("interactentity")) {
            return Priority.HIGH;
        }

        // LOW: informational, can be batched
        if (name.contains("statistic")
                || name.contains("advancement")
                || name.contains("recipe")
                || name.contains("tabcompletion")
                || name.contains("requestcommandsource")) {
            return Priority.LOW;
        }

        return Priority.NORMAL;
    }
}
