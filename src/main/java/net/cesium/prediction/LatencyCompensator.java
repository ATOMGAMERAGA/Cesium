package net.cesium.prediction;

import net.cesium.CesiumMod;
import net.cesium.monitoring.NetworkStatistics;

/**
 * Adjusts the interpolation delay of Cesium components based on the current
 * measured latency, acting as the single source of truth for lag-compensation
 * parameters used by {@link EntityInterpolator} and {@link JitterBuffer}.
 *
 * <p>Consumers call {@link #getInterpolationTicks()} each tick to retrieve
 * the recommended delay rather than computing it themselves.
 */
public class LatencyCompensator {

    // Thresholds (ms)
    private static final int LOW_PING    =  50;
    private static final int MEDIUM_PING = 150;

    private volatile int interpolationTicks = 2;
    private volatile double smoothedRtt = 0;
    private static final double ALPHA = 0.1; // EWMA factor

    public LatencyCompensator() {}

    /**
     * Should be called every server tick (or whenever a new RTT sample arrives).
     */
    public void update() {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;
            NetworkStatistics stats = engine.getNetworkStatistics();
            if (stats == null) return;

            int rawRtt = stats.getLastRttMs();
            if (rawRtt <= 0) return;

            // Exponentially-weighted moving average to smooth out spikes.
            smoothedRtt = smoothedRtt == 0
                    ? rawRtt
                    : (1 - ALPHA) * smoothedRtt + ALPHA * rawRtt;

            interpolationTicks = computeTicks((int) smoothedRtt);
        } catch (Exception ignored) {}
    }

    private static int computeTicks(int rttMs) {
        if (rttMs < LOW_PING)    return 1;
        if (rttMs < MEDIUM_PING) return 2;
        return 3;
    }

    /** Returns the recommended interpolation delay in game ticks. */
    public int getInterpolationTicks() { return interpolationTicks; }

    /** Returns the current smoothed RTT estimate in milliseconds. */
    public double getSmoothedRttMs() { return smoothedRtt; }
}
