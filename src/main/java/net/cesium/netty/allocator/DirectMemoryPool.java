package net.cesium.netty.allocator;

import io.netty.buffer.PooledByteBufAllocator;
import net.cesium.core.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies Minecraft-specific tuning to Netty's global
 * {@link PooledByteBufAllocator#DEFAULT} instance via system properties.
 *
 * <p>Must be called <em>before</em> any Netty channel is created (i.e., during
 * mod initialization), since Netty reads these properties at class-load time.
 *
 * <p>Tuning rationale:
 * <ul>
 *   <li>Minecraft networking is mostly single-threaded (one Netty I/O thread),
 *       so 4 arenas are more than enough.</li>
 *   <li>Most Minecraft packets fit in the 16–256 byte range, so the small
 *       cache is increased while the normal cache is kept moderate.</li>
 *   <li>On Android direct buffers are expensive; we disable them there.</li>
 * </ul>
 */
public final class DirectMemoryPool {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumDirectMemory");

    private DirectMemoryPool() {}

    /**
     * Sets system properties that govern {@link PooledByteBufAllocator}.
     * Call once from {@link net.cesium.CesiumMod#onInitializeClient()}.
     */
    public static void configure() {
        boolean android = PlatformDetector.isAndroid();

        // Number of heap arenas — Minecraft is mostly single-threaded.
        setIfAbsent("io.netty.allocator.numHeapArenas", "4");
        setIfAbsent("io.netty.allocator.numDirectArenas", android ? "0" : "4");

        // Enlarged small cache for Minecraft's typical packet size distribution.
        setIfAbsent("io.netty.allocator.smallCacheSize", "512");
        setIfAbsent("io.netty.allocator.normalCacheSize", "128");

        // Prefer direct buffers on non-Android.
        setIfAbsent("io.netty.noPreferDirect", android ? "true" : "false");

        // Reduce the max cached buffer capacity to avoid wasting memory between ticks.
        setIfAbsent("io.netty.allocator.maxCachedBufferCapacity", "131072"); // 128 KB

        LOGGER.info("[Cesium] DirectMemoryPool configured (android={})", android);
    }

    private static void setIfAbsent(String property, String value) {
        if (System.getProperty(property) == null) {
            System.setProperty(property, value);
        }
    }
}
