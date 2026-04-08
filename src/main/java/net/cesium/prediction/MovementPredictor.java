package net.cesium.prediction;

import net.cesium.CesiumConfig;

/**
 * Client-side movement prediction and reconciliation.
 *
 * <p>When the server sends a position correction, instead of snapping the
 * player immediately (causing jarring rubber-banding), this class applies a
 * smooth correction over several frames.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>On server position packet: compute error = server_pos − predicted_pos.</li>
 *   <li>If |error| < threshold: ignore (prediction was close enough).</li>
 *   <li>If |error| ≥ threshold: start smooth correction at {@code correctionSpeed}
 *       per frame.</li>
 * </ol>
 */
public class MovementPredictor {

    private final double correctionSpeed;
    private final double errorThreshold;

    // Pending correction vector
    private double corrX = 0, corrY = 0, corrZ = 0;
    private boolean correcting = false;

    public MovementPredictor(CesiumConfig config) {
        this.correctionSpeed = config.getCorrectionSpeed();
        this.errorThreshold = config.getErrorThresholdBlocks();
    }

    /**
     * Called when the server sends a position correction.
     *
     * @param serverX server-authoritative X
     * @param serverY server-authoritative Y
     * @param serverZ server-authoritative Z
     * @param clientX current client X
     * @param clientY current client Y
     * @param clientZ current client Z
     */
    public void onServerPositionReceived(double serverX, double serverY, double serverZ,
                                         double clientX, double clientY, double clientZ) {
        double errX = serverX - clientX;
        double errY = serverY - clientY;
        double errZ = serverZ - clientZ;
        double errorMag = Math.sqrt(errX * errX + errY * errY + errZ * errZ);

        if (errorMag < errorThreshold) {
            // Prediction was accurate enough — no correction needed.
            correcting = false;
            corrX = corrY = corrZ = 0;
            return;
        }

        // Start smooth correction.
        corrX = errX;
        corrY = errY;
        corrZ = errZ;
        correcting = true;
    }

    /**
     * Returns the correction delta to apply this frame and advances the correction.
     * Call once per client tick.
     *
     * @return {@code double[]{dx, dy, dz}} correction offset for this frame
     */
    public double[] tickCorrection() {
        if (!correcting) {
            return new double[]{0, 0, 0};
        }

        double dx = corrX * correctionSpeed;
        double dy = corrY * correctionSpeed;
        double dz = corrZ * correctionSpeed;

        corrX -= dx;
        corrY -= dy;
        corrZ -= dz;

        // Stop when correction is negligible.
        if (Math.abs(corrX) < 0.001 && Math.abs(corrY) < 0.001 && Math.abs(corrZ) < 0.001) {
            correcting = false;
            corrX = corrY = corrZ = 0;
        }

        return new double[]{dx, dy, dz};
    }

    public boolean isCorrectingPosition() { return correcting; }

    public void reset() {
        corrX = corrY = corrZ = 0;
        correcting = false;
    }
}
