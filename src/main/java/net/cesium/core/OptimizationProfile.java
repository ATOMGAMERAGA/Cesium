package net.cesium.core;

public enum OptimizationProfile {

    /**
     * Only safe optimizations enabled: flush consolidation, TCP tweaks, DNS cache.
     * No interpolation changes. Best compatibility.
     */
    CONSERVATIVE,

    /**
     * All optimizations active at moderate aggressiveness.
     * Suitable for most players.
     */
    BALANCED,

    /**
     * All optimizations at maximum. Best for very high-ping connections.
     * May increase CPU usage slightly.
     */
    AGGRESSIVE,

    /**
     * Auto-selected on Android (Mojo / Zalith / PojavLauncher).
     * No native libraries. Reduced memory/CPU footprint.
     */
    MOBILE,

    /**
     * All settings are configured manually by the user.
     */
    CUSTOM
}
