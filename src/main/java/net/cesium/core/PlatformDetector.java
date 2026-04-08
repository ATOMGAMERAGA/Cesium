package net.cesium.core;

import net.cesium.mobile.MobileDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PlatformDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumPlatform");

    public enum OS { WINDOWS, MACOS, LINUX, ANDROID, UNKNOWN }
    public enum Arch { X64, ARM64, X86, UNKNOWN }

    private static OS detectedOs = OS.UNKNOWN;
    private static Arch detectedArch = Arch.UNKNOWN;
    private static boolean detected = false;

    private PlatformDetector() {}

    public static void detect() {
        if (detected) return;

        if (MobileDetector.isAndroid()) {
            detectedOs = OS.ANDROID;
        } else {
            String osName = System.getProperty("os.name", "").toLowerCase();
            if (osName.contains("win")) {
                detectedOs = OS.WINDOWS;
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                detectedOs = OS.MACOS;
            } else if (osName.contains("linux")) {
                detectedOs = OS.LINUX;
            }
        }

        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            detectedArch = Arch.ARM64;
        } else if (arch.contains("amd64") || arch.contains("x86_64") || arch.contains("x64")) {
            detectedArch = Arch.X64;
        } else if (arch.contains("x86") || arch.contains("i386") || arch.contains("i686")) {
            detectedArch = Arch.X86;
        }

        detected = true;
        LOGGER.info("[Cesium] Detected OS={} Arch={}", detectedOs, detectedArch);
    }

    public static OS getOs() {
        return detectedOs;
    }

    public static Arch getArch() {
        return detectedArch;
    }

    public static boolean isWindows() { return detectedOs == OS.WINDOWS; }
    public static boolean isMacOs() { return detectedOs == OS.MACOS; }
    public static boolean isLinux() { return detectedOs == OS.LINUX; }
    public static boolean isAndroid() { return detectedOs == OS.ANDROID; }
    public static boolean isX64() { return detectedArch == Arch.X64; }
    public static boolean isArm64() { return detectedArch == Arch.ARM64; }

    /** Whether native Netty transports (Epoll/KQueue/io_uring) can be loaded. */
    public static boolean supportsNativeTransport() {
        return detectedOs == OS.LINUX || detectedOs == OS.MACOS;
    }

    /** Whether JNI native compression libraries can be loaded safely. */
    public static boolean supportsNativeCompression() {
        // Android JNI namespace restrictions make this risky; skip it.
        return detectedOs != OS.ANDROID;
    }

    public static String getPlatformDescription() {
        return detectedOs + " " + detectedArch
                + (MobileDetector.isPojavBased() ? " [PojavLauncher]" : "");
    }
}
