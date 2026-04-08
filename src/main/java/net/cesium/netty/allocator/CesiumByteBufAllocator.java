package net.cesium.netty.allocator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import net.cesium.CesiumConfig;
import net.cesium.core.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Custom ByteBuf allocator tuned for Minecraft's packet workload.
 *
 * <p>Uses {@link PooledByteBufAllocator} with adjusted arena counts and
 * cache sizes matching Minecraft's packet size distribution. On Android,
 * falls back to heap-based pooling since direct memory allocation is
 * expensive on that platform.
 */
public class CesiumByteBufAllocator implements ByteBufAllocator {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumAllocator");

    private final CesiumConfig config;
    private PooledByteBufAllocator delegate;
    private ScheduledExecutorService trimScheduler;

    public CesiumByteBufAllocator(CesiumConfig config) {
        this.config = config;
    }

    public void initialize() {
        // On Android, direct memory allocation is much more expensive, so use heap buffers.
        boolean preferDirect = config.isUseDirectBuffers() && !PlatformDetector.isAndroid();

        // Arena count: Minecraft networking is mostly single-threaded; more arenas waste RAM.
        int nHeapArena = Math.min(Runtime.getRuntime().availableProcessors(), 4);
        int nDirectArena = preferDirect ? nHeapArena : 0;

        // Cache sizes tuned to Minecraft's typical packet size distribution.
        // Most packets are 32–512 bytes; keep generous small/normal caches.
        int tinyCacheSize = 0;          // Netty 4.1 removed tiny; kept for compat
        int smallCacheSize = 256;
        int normalCacheSize = 128;

        delegate = new PooledByteBufAllocator(
                preferDirect,
                nHeapArena,
                nDirectArena,
                PooledByteBufAllocator.defaultPageSize(),
                PooledByteBufAllocator.defaultMaxOrder(),
                smallCacheSize,
                normalCacheSize,
                true  // useCacheForAllThreads
        );

        LOGGER.info("[Cesium] ByteBuf allocator: pooled (direct={}, arenas={})", preferDirect, nHeapArena);

        // Periodically trim thread-local caches to prevent memory leaks in long sessions.
        int trimIntervalSeconds = config.getCacheTrimIntervalSeconds();
        if (trimIntervalSeconds > 0) {
            trimScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cesium-cache-trimmer");
                t.setDaemon(true);
                return t;
            });
            trimScheduler.scheduleAtFixedRate(
                    delegate::trimCurrentThreadCache,
                    trimIntervalSeconds,
                    trimIntervalSeconds,
                    TimeUnit.SECONDS
            );
        }
    }

    public void shutdown() {
        if (trimScheduler != null) {
            trimScheduler.shutdownNow();
        }
    }

    public ByteBufAllocator getDelegate() {
        return delegate != null ? delegate : PooledByteBufAllocator.DEFAULT;
    }

    // ── ByteBufAllocator delegation ───────────────────────────────────────────

    @Override public ByteBuf buffer() { return getDelegate().buffer(); }
    @Override public ByteBuf buffer(int initialCapacity) { return getDelegate().buffer(initialCapacity); }
    @Override public ByteBuf buffer(int initialCapacity, int maxCapacity) { return getDelegate().buffer(initialCapacity, maxCapacity); }
    @Override public ByteBuf ioBuffer() { return getDelegate().ioBuffer(); }
    @Override public ByteBuf ioBuffer(int initialCapacity) { return getDelegate().ioBuffer(initialCapacity); }
    @Override public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) { return getDelegate().ioBuffer(initialCapacity, maxCapacity); }
    @Override public ByteBuf heapBuffer() { return getDelegate().heapBuffer(); }
    @Override public ByteBuf heapBuffer(int initialCapacity) { return getDelegate().heapBuffer(initialCapacity); }
    @Override public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) { return getDelegate().heapBuffer(initialCapacity, maxCapacity); }
    @Override public ByteBuf directBuffer() { return getDelegate().directBuffer(); }
    @Override public ByteBuf directBuffer(int initialCapacity) { return getDelegate().directBuffer(initialCapacity); }
    @Override public ByteBuf directBuffer(int initialCapacity, int maxCapacity) { return getDelegate().directBuffer(initialCapacity, maxCapacity); }
    @Override public io.netty.buffer.CompositeByteBuf compositeBuffer() { return getDelegate().compositeBuffer(); }
    @Override public io.netty.buffer.CompositeByteBuf compositeBuffer(int maxNumComponents) { return getDelegate().compositeBuffer(maxNumComponents); }
    @Override public io.netty.buffer.CompositeByteBuf compositeHeapBuffer() { return getDelegate().compositeHeapBuffer(); }
    @Override public io.netty.buffer.CompositeByteBuf compositeHeapBuffer(int maxNumComponents) { return getDelegate().compositeHeapBuffer(maxNumComponents); }
    @Override public io.netty.buffer.CompositeByteBuf compositeDirectBuffer() { return getDelegate().compositeDirectBuffer(); }
    @Override public io.netty.buffer.CompositeByteBuf compositeDirectBuffer(int maxNumComponents) { return getDelegate().compositeDirectBuffer(maxNumComponents); }
    @Override public boolean isDirectBufferPooled() { return getDelegate().isDirectBufferPooled(); }
    @Override public int calculateNewCapacity(int minNewCapacity, int maxCapacity) { return getDelegate().calculateNewCapacity(minNewCapacity, maxCapacity); }
}
