package net.cesium.monitoring;

import net.cesium.packet.PacketPriorityQueue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks per-packet-class statistics: count, total bytes, and average size.
 *
 * <p>Used by the detailed HUD mode and the config screen to show which packet
 * types consume the most bandwidth. Only active when
 * {@code monitoring.detailed_packet_stats} is enabled in config, as it adds a
 * small overhead per packet.
 */
public class PacketAnalyzer {

    public record PacketStats(long count, long totalBytes) {
        public double avgBytes() {
            return count == 0 ? 0 : (double) totalBytes / count;
        }
    }

    /** Key: simple class name of the packet; Value: counters. */
    private final ConcurrentHashMap<String, long[]> stats = new ConcurrentHashMap<>();

    /**
     * Records one packet of the given class and byte size.
     *
     * @param packetClassName simple or qualified class name
     * @param bytes           encoded size in bytes
     */
    public void record(String packetClassName, int bytes) {
        stats.computeIfAbsent(packetClassName, k -> new long[2]);
        long[] counters = stats.get(packetClassName);
        synchronized (counters) {
            counters[0]++;       // count
            counters[1] += bytes; // total bytes
        }
    }

    /**
     * Returns a snapshot of all recorded packet statistics, sorted by total
     * bytes descending (highest bandwidth consumers first).
     */
    public Map<String, PacketStats> getSnapshot() {
        ConcurrentHashMap<String, PacketStats> snapshot = new ConcurrentHashMap<>();
        stats.forEach((name, counters) -> {
            synchronized (counters) {
                snapshot.put(name, new PacketStats(counters[0], counters[1]));
            }
        });
        return snapshot;
    }

    /** Returns the priority tier classified for a given packet class name. */
    public PacketPriorityQueue.Priority getPriority(String packetClassName) {
        return PacketPriorityQueue.classify(packetClassName);
    }

    public void reset() {
        stats.clear();
    }
}
