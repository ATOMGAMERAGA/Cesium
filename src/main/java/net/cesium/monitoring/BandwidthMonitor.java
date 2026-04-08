package net.cesium.monitoring;

import java.util.concurrent.atomic.LongAdder;

/**
 * Tracks bandwidth usage (bytes/s) using a sliding 1-second window.
 *
 * <p>Two instances are typically maintained: one for upload, one for download.
 * Each second the accumulators are snapshotted and reset, producing a clean
 * bytes-per-second reading that the HUD can display.
 */
public class BandwidthMonitor {

    private final LongAdder accumulator = new LongAdder();
    private volatile long lastSnapshotMs = System.currentTimeMillis();
    private volatile long lastBytesPerSecond = 0;

    /** Records {@code bytes} passing through this direction. */
    public void record(long bytes) {
        accumulator.add(bytes);
        maybeSnapshot();
    }

    /** Returns the most recent bytes-per-second measurement. */
    public long getBytesPerSecond() {
        maybeSnapshot();
        return lastBytesPerSecond;
    }

    /** Returns bytes/s formatted as a human-readable string (e.g. "12.3 KB/s"). */
    public String getFormatted() {
        long bps = getBytesPerSecond();
        if (bps < 1024)           return bps + " B/s";
        if (bps < 1024 * 1024)    return String.format("%.1f KB/s", bps / 1024.0);
        return String.format("%.2f MB/s", bps / (1024.0 * 1024.0));
    }

    public void reset() {
        accumulator.reset();
        lastBytesPerSecond = 0;
        lastSnapshotMs = System.currentTimeMillis();
    }

    private void maybeSnapshot() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastSnapshotMs;
        if (elapsed >= 1000) {
            long total = accumulator.sumThenReset();
            lastBytesPerSecond = elapsed > 0 ? total * 1000 / elapsed : 0;
            lastSnapshotMs = now;
        }
    }
}
