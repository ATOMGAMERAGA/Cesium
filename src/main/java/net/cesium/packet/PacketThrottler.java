package net.cesium.packet;

import net.cesium.CesiumConfig;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Suppresses redundant outgoing packets to reduce unnecessary traffic.
 *
 * <p>Currently handles:
 * <ul>
 *   <li>Player position packets — skips if position delta is below threshold
 *       and enough time has elapsed since the last send.</li>
 * </ul>
 *
 * <p>Anti-cheat safety: the throttler guarantees at least one position
 * packet every {@code minPositionIntervalMs} milliseconds regardless of
 * delta, so servers never lose the heartbeat they need to keep the player
 * synchronized.
 */
public class PacketThrottler {

    private final int minPositionIntervalMs;
    private final double deltaThreshold;

    private double lastX = Double.NaN;
    private double lastY = Double.NaN;
    private double lastZ = Double.NaN;
    private long lastPositionSentAt = 0L;

    public PacketThrottler(CesiumConfig config) {
        this.minPositionIntervalMs = config.getMinPositionIntervalMs();
        this.deltaThreshold = config.getDeltaThreshold();
    }

    /**
     * Returns {@code true} if the given position packet should be suppressed.
     *
     * @param x       new X coordinate
     * @param y       new Y coordinate
     * @param z       new Z coordinate
     * @param nowMs   current time in milliseconds ({@link System#currentTimeMillis()})
     */
    public boolean shouldSuppressPosition(double x, double y, double z, long nowMs) {
        long elapsed = nowMs - lastPositionSentAt;

        // Always send at least every minPositionIntervalMs.
        if (elapsed >= minPositionIntervalMs) {
            recordPositionSent(x, y, z, nowMs);
            return false;
        }

        // Suppress if the position hasn't changed meaningfully.
        if (!Double.isNaN(lastX)) {
            double dx = Math.abs(x - lastX);
            double dy = Math.abs(y - lastY);
            double dz = Math.abs(z - lastZ);
            if (dx < deltaThreshold && dy < deltaThreshold && dz < deltaThreshold) {
                return true;
            }
        }

        recordPositionSent(x, y, z, nowMs);
        return false;
    }

    private void recordPositionSent(double x, double y, double z, long nowMs) {
        lastX = x;
        lastY = y;
        lastZ = z;
        lastPositionSentAt = nowMs;
    }

    public void reset() {
        lastX = Double.NaN;
        lastPositionSentAt = 0L;
    }
}
