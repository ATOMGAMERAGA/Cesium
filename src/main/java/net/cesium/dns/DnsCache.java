package net.cesium.dns;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory LRU DNS cache with optional disk persistence.
 *
 * <p>Cache eviction: entries expire when their TTL elapses. On eviction the
 * slot is reused (LRU). Negative results (NXDOMAIN) are cached for a shorter
 * period to avoid hammering unreachable hosts.
 *
 * <p>Disk persistence: on shutdown the cache is serialized to
 * {@code .minecraft/cache/cesium-dns.json}. On the next startup it is
 * reloaded, giving near-instant resolution for recently visited servers.
 */
public class DnsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumDns");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int NEGATIVE_TTL_SECONDS = 30;

    private final int maxEntries;
    private final int minTtlSeconds;
    private final boolean diskPersistence;

    /** hostname → CacheEntry */
    private final Map<String, CacheEntry> cache;

    public DnsCache(int maxEntries, int minTtlSeconds, boolean diskPersistence) {
        this.maxEntries = maxEntries;
        this.minTtlSeconds = minTtlSeconds;
        this.diskPersistence = diskPersistence;
        // LinkedHashMap with access-order=true gives us LRU eviction.
        this.cache = Collections.synchronizedMap(
                new LinkedHashMap<>(maxEntries, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                        return size() > maxEntries;
                    }
                });
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Returns cached addresses for {@code hostname}, or {@code null} if
     * not present / expired.
     */
    public InetAddress[] get(String hostname) {
        hostname = normalize(hostname);
        CacheEntry entry = cache.get(hostname);
        if (entry == null) return null;
        if (entry.isExpired()) {
            cache.remove(hostname);
            return null;
        }
        return entry.addresses;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Stores resolved addresses with the given TTL (floored to {@code minTtlSeconds}). */
    public void put(String hostname, InetAddress[] addresses, int ttlSeconds) {
        int effectiveTtl = Math.max(ttlSeconds, minTtlSeconds);
        cache.put(normalize(hostname),
                new CacheEntry(addresses, System.currentTimeMillis() + effectiveTtl * 1000L));
    }

    /** Stores a negative result (NXDOMAIN / resolution failure). */
    public void putNegative(String hostname) {
        cache.put(normalize(hostname),
                new CacheEntry(null, System.currentTimeMillis() + NEGATIVE_TTL_SECONDS * 1000L));
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    public void loadFromDisk() {
        if (!diskPersistence) return;
        Path path = cachePath();
        if (!Files.exists(path)) return;

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                String host = obj.get("host").getAsString();
                long expiry = obj.get("expiry").getAsLong();
                if (expiry <= System.currentTimeMillis()) continue; // already stale

                JsonArray addrs = obj.getAsJsonArray("addresses");
                InetAddress[] addresses = new InetAddress[addrs.size()];
                for (int i = 0; i < addrs.size(); i++) {
                    addresses[i] = InetAddress.getByName(addrs.get(i).getAsString());
                }
                cache.put(host, new CacheEntry(addresses, expiry));
            }
            LOGGER.info("[Cesium] DNS cache loaded ({} entries) from {}", cache.size(), path);
        } catch (Exception e) {
            LOGGER.warn("[Cesium] Failed to load DNS cache: {}", e.getMessage());
        }
    }

    public void saveToDisk() {
        if (!diskPersistence) return;
        Path path = cachePath();

        JsonArray array = new JsonArray();
        synchronized (cache) {
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                CacheEntry ce = entry.getValue();
                if (ce.isExpired() || ce.addresses == null) continue;

                JsonObject obj = new JsonObject();
                obj.addProperty("host", entry.getKey());
                obj.addProperty("expiry", ce.expiry);
                JsonArray addrs = new JsonArray();
                for (InetAddress addr : ce.addresses) {
                    addrs.add(addr.getHostAddress());
                }
                obj.add("addresses", addrs);
                array.add(obj);
            }
        }

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(array, writer);
            }
            LOGGER.debug("[Cesium] DNS cache saved ({} entries)", array.size());
        } catch (IOException e) {
            LOGGER.warn("[Cesium] Failed to save DNS cache: {}", e.getMessage());
        }
    }

    public void invalidate(String hostname) {
        cache.remove(normalize(hostname));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static String normalize(String hostname) {
        return hostname.toLowerCase(Locale.ROOT).trim();
    }

    private static Path cachePath() {
        return FabricLoader.getInstance().getGameDir()
                .resolve("cache").resolve("cesium-dns.json");
    }

    private record CacheEntry(InetAddress[] addresses, long expiry) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiry;
        }
    }
}
