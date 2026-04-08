package net.cesium.dns;

import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;

/**
 * Asynchronous DNS resolver backed by Java 21 virtual threads.
 *
 * <p>Resolves A/AAAA records concurrently for a given hostname.
 * Results are stored in {@link DnsCache} for subsequent lookups.
 */
public class AsyncDnsResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumDnsResolver");
    private static final int RESOLVE_TIMEOUT_MS = 5000;

    private final DnsCache cache;
    private final CesiumConfig config;
    private final ExecutorService executor;

    public AsyncDnsResolver(DnsCache cache, CesiumConfig config) {
        this.cache = cache;
        this.config = config;
        // Virtual-thread executor — each resolve task gets its own VT.
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Resolves {@code hostname} asynchronously.
     *
     * @return future that completes with the resolved addresses,
     *         or an empty array on failure
     */
    public CompletableFuture<InetAddress[]> resolveAsync(String hostname) {
        // Check cache first.
        if (cache != null) {
            InetAddress[] cached = cache.get(hostname);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }

        return CompletableFuture.supplyAsync(() -> resolve(hostname), executor)
                .orTimeout(RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    LOGGER.debug("[Cesium] DNS resolution failed for {}: {}", hostname, ex.getMessage());
                    if (cache != null) cache.putNegative(hostname);
                    return new InetAddress[0];
                });
    }

    /**
     * Blocking resolve (run inside a virtual thread to avoid blocking carrier threads).
     */
    private InetAddress[] resolve(String hostname) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            if (cache != null && addresses.length > 0) {
                // Default TTL: use minTtlSeconds from config (or 60 s).
                cache.put(hostname, addresses, config.getDnsCacheMinTtlSeconds());
            }
            return addresses;
        } catch (UnknownHostException e) {
            if (cache != null) cache.putNegative(hostname);
            return new InetAddress[0];
        }
    }

    /**
     * Warms up the DNS cache for a list of hostnames in the background.
     * Typically called with the server list entries on startup.
     */
    public void warmUp(Iterable<String> hostnames) {
        for (String hostname : hostnames) {
            resolveAsync(hostname); // fire-and-forget
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
