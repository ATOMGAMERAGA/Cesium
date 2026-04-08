package net.cesium.mobile;

/**
 * Describes the active optimization tier on a mobile device.
 *
 * <p>Selected automatically by {@link MobileMemoryGuard} based on available
 * heap memory, and further adjusted by {@link MobileBatteryAware} at runtime.
 */
public enum MobileOptimizationProfile {

    /**
     * Ultra-low-memory devices (< 512 MB assigned to Minecraft).
     * Most statistics and visual features are disabled.
     */
    ULTRA_LOW,

    /**
     * Low-memory devices (512 MB – 1 GB).
     * Reduced history buffers; no ping graph.
     */
    LOW,

    /**
     * Normal mobile devices (1 – 2 GB).
     * Full feature set minus CPU-intensive items (spline, native libs).
     */
    NORMAL,

    /**
     * Higher-end devices (> 2 GB).
     * Approaches desktop BALANCED profile; native libraries still disabled.
     */
    COMFORTABLE;

    /**
     * Derives the appropriate profile from the available heap size reported by
     * {@link MobileMemoryGuard#getTier()}.
     */
    public static MobileOptimizationProfile from(MobileMemoryGuard.MemoryTier tier) {
        return switch (tier) {
            case ULTRA_LOW   -> ULTRA_LOW;
            case LOW         -> LOW;
            case NORMAL      -> NORMAL;
            case COMFORTABLE -> COMFORTABLE;
        };
    }
}
