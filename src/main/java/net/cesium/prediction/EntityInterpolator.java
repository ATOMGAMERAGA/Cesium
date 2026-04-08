package net.cesium.prediction;

import net.cesium.CesiumConfig;

/**
 * Improves entity position interpolation using Hermite spline curves.
 *
 * <p>Vanilla Minecraft uses linear interpolation between two known positions.
 * This class uses the last four known positions (when available) to compute
 * a Catmull-Rom spline, producing smoother, more natural-looking motion.
 *
 * <p>On mobile (or when {@code splineInterpolation} is disabled in config),
 * falls back to linear interpolation for lower CPU usage.
 */
public class EntityInterpolator {

    private final boolean useSpline;
    private final boolean adaptiveDelay;

    // Ring buffer of the last 4 known positions
    private final double[] histX = new double[4];
    private final double[] histY = new double[4];
    private final double[] histZ = new double[4];
    private int histCount = 0;

    public EntityInterpolator(CesiumConfig config) {
        this.useSpline = config.isSplineInterpolation();
        this.adaptiveDelay = config.isAdaptiveInterpolationDelay();
    }

    /**
     * Adds a new known position to the history.
     *
     * @param x server-reported X
     * @param y server-reported Y
     * @param z server-reported Z
     */
    public void addPosition(double x, double y, double z) {
        // Shift ring buffer
        int count = Math.min(histCount, 4);
        for (int i = count; i > 0; i--) {
            histX[i] = histX[i - 1];
            histY[i] = histY[i - 1];
            histZ[i] = histZ[i - 1];
        }
        histX[0] = x;
        histY[0] = y;
        histZ[0] = z;
        histCount++;
    }

    /**
     * Computes the interpolated position at fractional tick {@code t ∈ [0, 1]}.
     *
     * @param t interpolation factor (0 = previous position, 1 = current position)
     * @return interpolated position as {@code double[]{x, y, z}}
     */
    public double[] interpolate(double t) {
        if (histCount < 2) {
            return new double[]{histX[0], histY[0], histZ[0]};
        }

        if (useSpline && histCount >= 4) {
            return catmullRom(histX, histY, histZ, t);
        }

        // Linear fallback
        return new double[]{
                lerp(histX[1], histX[0], t),
                lerp(histY[1], histY[0], t),
                lerp(histZ[1], histZ[0], t)
        };
    }

    /**
     * Returns the recommended interpolation delay (in ticks) based on the
     * current ping estimate.
     *
     * @param pingMs current RTT in milliseconds
     */
    public int getInterpolationDelayTicks(int pingMs) {
        if (!adaptiveDelay) return 2;
        if (pingMs < 50) return 1;
        if (pingMs < 150) return 2;
        return 3;
    }

    public void reset() {
        histCount = 0;
    }

    // ── Math helpers ─────────────────────────────────────────────────────────

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Catmull-Rom spline interpolation using 4 control points.
     * Assumes points are stored newest-first in each array.
     */
    private static double[] catmullRom(double[] xs, double[] ys, double[] zs, double t) {
        // Points: p0=oldest, p1=prev, p2=current, p3=newest(future)
        // We interpolate between p1 and p2.
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2 * xs[1])
                + (-xs[2] + xs[0]) * t
                + (2 * xs[2] - 5 * xs[1] + 4 * xs[0] - xs[3]) * t2  // NOSONAR
                + (-xs[2] + 3 * xs[1] - 3 * xs[0] + xs[3]) * t3);

        double y = 0.5 * ((2 * ys[1])
                + (-ys[2] + ys[0]) * t
                + (2 * ys[2] - 5 * ys[1] + 4 * ys[0] - ys[3]) * t2
                + (-ys[2] + 3 * ys[1] - 3 * ys[0] + ys[3]) * t3);

        double z = 0.5 * ((2 * zs[1])
                + (-zs[2] + zs[0]) * t
                + (2 * zs[2] - 5 * zs[1] + 4 * zs[0] - zs[3]) * t2
                + (-zs[2] + 3 * zs[1] - 3 * zs[0] + zs[3]) * t3);

        return new double[]{x, y, z};
    }
}
