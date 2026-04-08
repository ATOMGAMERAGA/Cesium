package net.cesium.mobile;

import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adapts Cesium's resource usage based on Android battery level.
 *
 * <p>Battery levels are queried via reflection so the class compiles and loads
 * on desktop JVMs without Android APIs.
 *
 * <p>Behavior tiers:
 * <ul>
 *   <li>Charging or > {@code lowThreshold}%: full optimization</li>
 *   <li>{@code criticalThreshold}% – {@code lowThreshold}%: reduce compression CPU</li>
 *   <li>< {@code criticalThreshold}%: minimal mode (flush consolidation only)</li>
 * </ul>
 */
public class MobileBatteryAware {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumBattery");

    public enum BatteryMode { FULL, REDUCED, MINIMAL }

    private final int lowThreshold;
    private final int criticalThreshold;
    private volatile BatteryMode currentMode = BatteryMode.FULL;
    private volatile boolean charging = false;
    private volatile int lastBatteryPercent = -1;

    public MobileBatteryAware(CesiumConfig config) {
        this.lowThreshold = config.getBatteryLowThresholdPercent();
        this.criticalThreshold = config.getBatteryCriticalThresholdPercent();
    }

    /**
     * Refreshes battery status. Should be called every 30 seconds or so.
     * Uses reflection to access {@code android.os.BatteryManager}.
     */
    public void refresh() {
        if (!MobileDetector.isAndroid()) return;

        try {
            // Retrieve battery level via android.os.SystemProperties or BatteryManager
            // We use a property-based approach because it doesn't require a Context.
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            // Battery level isn't directly available via SystemProperties; read from
            // /sys/class/power_supply/battery/capacity if accessible.
            String capacityStr = readSysFile("/sys/class/power_supply/battery/capacity");
            if (capacityStr != null) {
                int percent = Integer.parseInt(capacityStr.trim());
                updateMode(percent, readSysFile("/sys/class/power_supply/battery/status"));
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Battery status unavailable: {}", e.getMessage());
        }
    }

    private void updateMode(int percent, String statusStr) {
        lastBatteryPercent = percent;
        charging = statusStr != null && (statusStr.contains("Charging") || statusStr.contains("Full"));

        BatteryMode newMode;
        if (charging || percent > lowThreshold) {
            newMode = BatteryMode.FULL;
        } else if (percent > criticalThreshold) {
            newMode = BatteryMode.REDUCED;
        } else {
            newMode = BatteryMode.MINIMAL;
        }

        if (newMode != currentMode) {
            currentMode = newMode;
            LOGGER.info("[Cesium] Battery mode changed: {} ({}% {})",
                    currentMode, percent, charging ? "charging" : "discharging");
        }
    }

    private static String readSysFile(String path) {
        try {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path))).trim();
        } catch (Exception e) {
            return null;
        }
    }

    public BatteryMode getCurrentMode() { return currentMode; }
    public int getLastBatteryPercent() { return lastBatteryPercent; }
    public boolean isCharging() { return charging; }

    /** Returns {@code true} if high-CPU operations (compression, spline) should be skipped. */
    public boolean shouldReduceCpu() {
        return currentMode == BatteryMode.REDUCED || currentMode == BatteryMode.MINIMAL;
    }

    /** Returns {@code true} if statistics collection should be paused. */
    public boolean shouldPauseStatistics() {
        return currentMode == BatteryMode.MINIMAL;
    }
}
