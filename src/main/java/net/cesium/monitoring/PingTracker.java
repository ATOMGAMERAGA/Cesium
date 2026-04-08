package net.cesium.monitoring;

import net.cesium.CesiumMod;

/**
 * Tracks per-KeepAlive round-trip times and updates {@link NetworkStatistics}.
 *
 * <p>Usage pattern (from {@code KeepAliveOptimizer}):
 * <pre>
 *   long id = pingTracker.onKeepAliveSent(keepAliveId);
 *   // ... later when the KeepAlive response arrives ...
 *   pingTracker.onKeepAliveReceived(keepAliveId);
 * </pre>
 */
public class PingTracker {

    private static final int MAX_TRACKED = 128;

    // Ring buffer: maps keepAlive ID → sent timestamp
    private final long[] ids = new long[MAX_TRACKED];
    private final long[] sentAt = new long[MAX_TRACKED];
    private int head = 0;

    private volatile int lastPingMs = 0;

    public PingTracker() {}

    /** Records the moment a KeepAlive packet is sent. */
    public void onKeepAliveSent(long keepAliveId) {
        int slot = head % MAX_TRACKED;
        ids[slot] = keepAliveId;
        sentAt[slot] = System.currentTimeMillis();
        head++;
    }

    /** Processes an incoming KeepAlive response and records the RTT. */
    public void onKeepAliveReceived(long keepAliveId) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < MAX_TRACKED; i++) {
            if (ids[i] == keepAliveId && sentAt[i] != 0) {
                int rtt = (int) (now - sentAt[i]);
                lastPingMs = rtt;
                sentAt[i] = 0; // mark as consumed
                recordToStats(rtt);
                return;
            }
        }
    }

    public int getLastPingMs() { return lastPingMs; }

    private void recordToStats(int rttMs) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine != null) {
                NetworkStatistics stats = engine.getNetworkStatistics();
                if (stats != null) {
                    stats.recordRtt(rttMs);
                }
            }
        } catch (Exception ignored) {}
    }
}
