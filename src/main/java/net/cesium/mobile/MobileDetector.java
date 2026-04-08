package net.cesium.mobile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether Cesium is running inside an Android-based Minecraft launcher
 * (PojavLauncher, Mojo Launcher, Zalith Launcher, Amethyst, etc.).
 *
 * <p>Detection strategy uses four independent checks to maximize reliability
 * across the varied class structures of different launcher versions.
 */
public final class MobileDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumMobile");

    private static Boolean cachedAndroid = null;
    private static Boolean cachedPojav = null;

    private MobileDetector() {}

    /**
     * Returns {@code true} if running on an Android device.
     * Result is cached after the first call.
     */
    public static boolean isAndroid() {
        if (cachedAndroid != null) return cachedAndroid;
        cachedAndroid = detectAndroid();
        if (cachedAndroid) {
            LOGGER.info("[Cesium] Android environment detected — MOBILE optimizations will activate.");
        }
        return cachedAndroid;
    }

    /**
     * Returns {@code true} if the launcher is PojavLauncher-based
     * (Pojav, Mojo, Zalith, Amethyst).
     */
    public static boolean isPojavBased() {
        if (cachedPojav != null) return cachedPojav;
        cachedPojav = detectPojav();
        return cachedPojav;
    }

    public static int getAvailableRamMb() {
        return (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L));
    }

    public static boolean isLowMemoryDevice() {
        return getAvailableRamMb() < 1024;
    }

    // ── Detection internals ───────────────────────────────────────────────────

    private static boolean detectAndroid() {
        // Check 1: android.os.Build is always present on Android JVMs
        if (classExists("android.os.Build")) return true;

        // Check 2: JVM name contains "android" (Dalvik/ART)
        String vmName = System.getProperty("java.vm.name", "");
        if (vmName.toLowerCase().contains("android")) return true;

        // Check 3: Android-specific system property
        String vmVendor = System.getProperty("java.vm.vendor", "");
        if (vmVendor.toLowerCase().contains("android")) return true;

        // Check 4: Running on Linux ARM64 with android.app.Activity present
        String osName = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (osName.contains("linux") && (arch.contains("aarch64") || arch.contains("arm"))) {
            if (classExists("android.app.Activity")) return true;
        }

        return false;
    }

    private static boolean detectPojav() {
        if (!isAndroid()) return false;
        // Look for any known PojavLauncher-family class
        return classExists("net.kdt.pojavlaunch.PojavApplication")
                || classExists("net.kdt.pojavlaunch.MainActivity")
                || classExists("com.movtery.zalithlauncher.ZalithApplication")
                || classExists("git.artdeell.mojo.MojoApplication")
                || classExists("net.kdt.pojavlaunch.AmethystApplication");
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
