package net.cesium.packet;

import net.cesium.CesiumMod;
import net.cesium.netty.transport.NativeCompressionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.zip.Deflater;

/**
 * Selects the appropriate compression algorithm and level based on packet size.
 *
 * <pre>
 *   < threshold   → pass-through (handled by caller)
 *   128 – 1 KB    → LZ4 fast (if available)
 *   1 KB – 32 KB  → Zstandard level 3
 *   > 32 KB       → Zstandard level 6
 * </pre>
 *
 * Falls back gracefully to zlib if native libraries are not available.
 */
public class AdaptiveCompressionStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumCompression");

    private static final int LZ4_THRESHOLD  =  1 * 1024;   //  1 KB
    private static final int ZSTD_L3_MAX    = 32 * 1024;   // 32 KB

    private final boolean zstdAvailable;
    private final boolean lz4Available;
    private final NativeCompressionLoader.CompressionBackend zstdBackend;
    private final NativeCompressionLoader.CompressionBackend lz4Backend;

    public AdaptiveCompressionStrategy() {
        NativeCompressionLoader loader = null;
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine != null) {
                loader = engine.getNativeCompressionLoader();
            }
        } catch (Exception ignored) {}

        if (loader != null) {
            zstdAvailable = loader.isZstdAvailable();
            lz4Available = loader.isLz4Available();
            zstdBackend = loader.getZstdBackend();
            lz4Backend = loader.getLz4Backend();
        } else {
            zstdAvailable = false;
            lz4Available = false;
            zstdBackend = NativeCompressionLoader.CompressionBackend.ZLIB_DEFLATER;
            lz4Backend = NativeCompressionLoader.CompressionBackend.ZLIB_DEFLATER;
        }
    }

    /**
     * Compresses {@code input} using the best available algorithm.
     *
     * @param input    raw packet bytes
     * @param deflater a reusable {@link Deflater} for zlib fallback
     * @return compressed bytes, or {@code null} if compression failed
     */
    public byte[] compress(byte[] input, Deflater deflater) {
        int length = input.length;

        if (length < LZ4_THRESHOLD && lz4Available) {
            return compressLz4(input);
        }
        if (zstdAvailable) {
            int level = length > ZSTD_L3_MAX ? 6 : 3;
            return compressZstd(input, level);
        }

        return compressZlib(input, deflater);
    }

    // ── Backend implementations ───────────────────────────────────────────────

    private byte[] compressLz4(byte[] input) {
        try {
            Class<?> factoryClass = Class.forName("net.jpountz.lz4.LZ4Factory");
            Object factory;
            if (lz4Backend == NativeCompressionLoader.CompressionBackend.LZ4_NATIVE) {
                factory = factoryClass.getMethod("nativeInstance").invoke(null);
            } else {
                factory = factoryClass.getMethod("safeInstance").invoke(null);
            }
            Object compressor = factoryClass.getMethod("fastCompressor").invoke(factory);
            Class<?> compressorClass = compressor.getClass();

            int maxCompressedLength = (int) compressorClass
                    .getMethod("maxCompressedLength", int.class)
                    .invoke(compressor, input.length);
            byte[] output = new byte[maxCompressedLength];
            int compressedLength = (int) compressorClass
                    .getMethod("compress", byte[].class, int.class, int.class, byte[].class, int.class)
                    .invoke(compressor, input, 0, input.length, output, 0);

            byte[] result = new byte[compressedLength];
            System.arraycopy(output, 0, result, 0, compressedLength);
            return result;
        } catch (Exception e) {
            LOGGER.debug("[Cesium] LZ4 compression failed, falling back: {}", e.getMessage());
            return null;
        }
    }

    private byte[] compressZstd(byte[] input, int level) {
        try {
            if (zstdBackend == NativeCompressionLoader.CompressionBackend.ZSTD_JNI) {
                Class<?> zstd = Class.forName("com.github.luben.zstd.Zstd");
                return (byte[]) zstd.getMethod("compress", byte[].class, int.class)
                        .invoke(null, input, level);
            }
            // For Netty native Zstd, we'd need a ChannelHandlerContext — fall through to zlib.
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Zstd compression failed, falling back: {}", e.getMessage());
        }
        return null;
    }

    private byte[] compressZlib(byte[] input, Deflater deflater) {
        deflater.reset();
        deflater.setInput(input);
        deflater.finish();

        byte[] output = new byte[input.length];
        int compressed = deflater.deflate(output);

        if (compressed <= 0 || !deflater.finished()) {
            return null;
        }

        byte[] result = new byte[compressed];
        System.arraycopy(output, 0, result, 0, compressed);
        return result;
    }
}
