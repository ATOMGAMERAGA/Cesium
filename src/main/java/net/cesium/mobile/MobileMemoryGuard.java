package net.cesium.mobile;

import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adjusts Cesium's runtime behavior based on the available heap size on
 * Android devices to prevent OutOfMemoryErrors.
 */
public class MobileMemoryGuard {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumMemGuard");

    public enum MemoryTier {
        ULTRA_LOW,  // < 512 MB
        LOW,        // 512 MB – 1 GB
        NORMAL,     // 1 GB – 2 GB
        COMFORTABLE // > 2 GB
    }

    private final MemoryTier tier;

    public MobileMemoryGuard(CesiumConfig config) {
        int ramMb = MobileDetector.getAvailableRamMb();
        this.tier = classifyRam(ramMb);
        LOGGER.info("[Cesium] Mobile RAM tier: {} ({}MB available)", tier, ramMb);
        if (config.isMemoryGuard()) {
            applyGuard(config);
        }
    }

    private static MemoryTier classifyRam(int ramMb) {
        if (ramMb < 512) return MemoryTier.ULTRA_LOW;
        if (ramMb < 1024) return MemoryTier.LOW;
        if (ramMb < 2048) return MemoryTier.NORMAL;
        return MemoryTier.COMFORTABLE;
    }

    private void applyGuard(CesiumConfig config) {
        // These are advisory — actual enforcement happens in the components
        // that read the tier.
        switch (tier) {
            case ULTRA_LOW -> LOGGER.warn("[Cesium] Ultra-low memory device detected. " +
                    "Statistics and ping graph will be disabled to preserve heap space.");
            case LOW -> LOGGER.info("[Cesium] Low memory device. " +
                    "Reducing buffer pool and history sizes.");
            default -> { /* No action needed. */ }
        }
    }

    /** Returns the maximum DNS cache entries appropriate for this tier. */
    public int getDnsCacheSize() {
        return switch (tier) {
            case ULTRA_LOW -> 64;
            case LOW -> 128;
            case NORMAL -> 256;
            case COMFORTABLE -> 512;
        };
    }

    /** Returns the statistics history duration in minutes appropriate for this tier. */
    public int getHistoryDurationMinutes() {
        return switch (tier) {
            case ULTRA_LOW -> 0;   // disabled
            case LOW -> 1;
            case NORMAL -> 2;
            case COMFORTABLE -> 5;
        };
    }

    /** Returns the ping graph duration in seconds appropriate for this tier. */
    public int getGraphDurationSeconds() {
        return switch (tier) {
            case ULTRA_LOW -> 0;   // disabled
            case LOW -> 15;
            case NORMAL -> 30;
            case COMFORTABLE -> 60;
        };
    }

    /** Returns the recommended buffer pool size (bytes) for this tier. */
    public int getBufferPoolSizeBytes() {
        return switch (tier) {
            case ULTRA_LOW -> 32 * 1024;
            case LOW -> 64 * 1024;
            case NORMAL -> 128 * 1024;
            case COMFORTABLE -> 256 * 1024;
        };
    }

    public MemoryTier getTier() { return tier; }
}
