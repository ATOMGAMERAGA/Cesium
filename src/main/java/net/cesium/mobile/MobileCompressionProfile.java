package net.cesium.mobile;

import net.cesium.CesiumConfig;

import java.util.zip.Deflater;

/**
 * Provides a pure-Java compression strategy tuned for mobile CPU constraints.
 *
 * <p>On Android, native Zstandard and LZ4 libraries cannot be loaded safely.
 * This profile uses {@link java.util.zip.Deflater} at reduced compression levels
 * to balance bandwidth savings against CPU cost.
 *
 * <p>Thresholds (larger than desktop to reduce CPU usage):
 * <pre>
 *   < 256 byte  → no compression
 *   256–2048    → zlib level 1 (fastest)
 *   > 2048      → zlib level 3 (moderate)
 * </pre>
 *
 * <p>On low-battery ({@link MobileBatteryAware.BatteryMode#MINIMAL}) all
 * compression is disabled to eliminate CPU overhead entirely.
 */
public class MobileCompressionProfile {

    private static final int NO_COMPRESS_THRESHOLD  =  256;
    private static final int FAST_COMPRESS_THRESHOLD = 2048;

    private final MobileBatteryAware batteryAware;

    public MobileCompressionProfile(CesiumConfig config, MobileBatteryAware batteryAware) {
        this.batteryAware = batteryAware;
    }

    /**
     * Compresses {@code input} using the mobile-appropriate zlib level.
     *
     * @param input    raw packet bytes
     * @param deflater a reusable {@link Deflater} — caller must not call finish()
     * @return compressed bytes, or {@code null} if skipped
     */
    public byte[] compress(byte[] input, Deflater deflater) {
        if (batteryAware.getCurrentMode() == MobileBatteryAware.BatteryMode.MINIMAL) {
            return null; // battery critical — skip compression entirely
        }

        int length = input.length;
        if (length < NO_COMPRESS_THRESHOLD) return null;

        int level = length < FAST_COMPRESS_THRESHOLD ? Deflater.BEST_SPEED : 3;
        deflater.reset();
        deflater.setLevel(level);
        deflater.setInput(input);
        deflater.finish();

        byte[] output = new byte[length];
        int compressed = deflater.deflate(output);

        if (compressed <= 0 || !deflater.finished() || compressed >= length) {
            return null; // no gain — send uncompressed
        }

        byte[] result = new byte[compressed];
        System.arraycopy(output, 0, result, 0, compressed);
        return result;
    }
}
