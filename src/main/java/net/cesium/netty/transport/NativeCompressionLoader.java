package net.cesium.netty.transport;

import net.cesium.CesiumConfig;
import net.cesium.core.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the best available compression backend.
 *
 * <p>Fallback chain:
 * <ol>
 *   <li>Netty native Zstd (io.netty.handler.codec.compression.Zstd)</li>
 *   <li>zstd-jni (JNI binding)</li>
 *   <li>Pure-Java Zstandard</li>
 *   <li>java.util.zip.Deflater (vanilla behavior)</li>
 * </ol>
 */
public class NativeCompressionLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumCompression");

    public enum CompressionBackend {
        ZSTD_NATIVE, ZSTD_JNI, LZ4_NATIVE, LZ4_JAVA, ZLIB_DEFLATER
    }

    private final CesiumConfig config;
    private CompressionBackend zstdBackend = CompressionBackend.ZLIB_DEFLATER;
    private CompressionBackend lz4Backend = CompressionBackend.ZLIB_DEFLATER;
    private boolean zstdAvailable = false;
    private boolean lz4Available = false;

    public NativeCompressionLoader(CesiumConfig config) {
        this.config = config;
    }

    public void load() {
        if (!config.isCompressionEnabled()) {
            LOGGER.info("[Cesium] Compression engine: disabled by config");
            return;
        }

        boolean useNative = config.isPreferNativeCompression()
                && PlatformDetector.supportsNativeCompression()
                && !config.isDisableNativeLibraries();

        if (useNative) {
            tryLoadZstdNative();
            tryLoadLz4Native();
        } else {
            tryLoadZstdJni();
            tryLoadLz4Java();
        }

        LOGGER.info("[Cesium] Compression backends — Zstd: {} | LZ4: {}",
                zstdBackend, lz4Backend);
    }

    private void tryLoadZstdNative() {
        try {
            Class<?> zstd = Class.forName("io.netty.handler.codec.compression.Zstd");
            boolean available = (boolean) zstd.getMethod("isAvailable").invoke(null);
            if (available) {
                zstdBackend = CompressionBackend.ZSTD_NATIVE;
                zstdAvailable = true;
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Netty native Zstd unavailable: {}", e.getMessage());
        }
        tryLoadZstdJni();
    }

    private void tryLoadZstdJni() {
        try {
            Class.forName("com.github.luben.zstd.Zstd");
            zstdBackend = CompressionBackend.ZSTD_JNI;
            zstdAvailable = true;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("[Cesium] zstd-jni unavailable, falling back to zlib");
            zstdBackend = CompressionBackend.ZLIB_DEFLATER;
            zstdAvailable = false;
        }
    }

    private void tryLoadLz4Native() {
        try {
            Class<?> lz4 = Class.forName("net.jpountz.lz4.LZ4Factory");
            Object factory = lz4.getMethod("nativeInstance").invoke(null);
            if (factory != null) {
                lz4Backend = CompressionBackend.LZ4_NATIVE;
                lz4Available = true;
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] LZ4 native unavailable: {}", e.getMessage());
        }
        tryLoadLz4Java();
    }

    private void tryLoadLz4Java() {
        try {
            Class<?> lz4 = Class.forName("net.jpountz.lz4.LZ4Factory");
            Object factory = lz4.getMethod("safeInstance").invoke(null);
            if (factory != null) {
                lz4Backend = CompressionBackend.LZ4_JAVA;
                lz4Available = true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] LZ4 Java unavailable: {}", e.getMessage());
            lz4Available = false;
        }
    }

    public CompressionBackend getZstdBackend() { return zstdBackend; }
    public CompressionBackend getLz4Backend() { return lz4Backend; }
    public boolean isZstdAvailable() { return zstdAvailable; }
    public boolean isLz4Available() { return lz4Available; }
}
