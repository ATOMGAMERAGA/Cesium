package net.cesium;

import com.google.gson.*;
import net.cesium.core.OptimizationProfile;
import net.cesium.mobile.MobileDetector;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;

public class CesiumConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private OptimizationProfile profile;

    // Network
    private boolean flushConsolidationEnabled = true;
    private int explicitFlushAfterFlushes = 256;
    private boolean compressionEnabled = true;
    private String compressionAlgorithm = "AUTO";
    private boolean adaptiveCompression = true;
    private boolean dictionarySharing = true;
    private int smallPacketThreshold = 128;
    private boolean preferNativeCompression = true;
    private boolean useDirectBuffers = true;
    private int cacheTrimIntervalSeconds = 30;
    private boolean tcpNoDelay = true;
    private int ipTos = 24;
    private boolean autoTuneBuffers = true;
    private boolean tcpKeepalive = true;
    private boolean nativeTransportEnabled = true;
    private boolean preferIoUring = true;

    // DNS
    private boolean asyncDnsResolve = true;
    private boolean dnsCacheEnabled = true;
    private int dnsCacheMaxEntries = 512;
    private int dnsCacheMinTtlSeconds = 60;
    private boolean dnsDiskPersistence = true;
    private boolean happyEyeballs = true;
    private boolean dnsOverHttps = false;

    // Packets
    private boolean priorityQueueEnabled = true;
    private boolean coalescingEnabled = true;
    private int coalesceWindowMs = 5;
    private boolean throttlingEnabled = true;
    private int minPositionIntervalMs = 50;
    private double deltaThreshold = 0.001;

    // Prediction
    private boolean enhancedMovementPrediction = true;
    private double correctionSpeed = 0.3;
    private double errorThresholdBlocks = 0.5;
    private boolean splineInterpolation = true;
    private boolean adaptiveInterpolationDelay = true;
    private boolean jitterBufferEnabled = true;
    private String jitterBufferMode = "ADAPTIVE";
    private int jitterBufferMinSize = 1;
    private int jitterBufferMaxSize = 5;

    // Connection
    private boolean autoReconnect = false;
    private int reconnectMaxAttempts = 5;
    private int reconnectBackoffBaseMs = 1000;
    private int reconnectBackoffMaxMs = 30000;
    private boolean timeoutAdaptive = true;
    private boolean keepalivePriority = true;

    // Monitoring
    private boolean collectStatistics = true;
    private boolean detailedPacketStats = false;
    private int historyDurationMinutes = 5;

    // HUD
    private boolean hudEnabled = true;
    private String hudMode = "MINIMAL";
    private String hudPosition = "TOP_LEFT";
    private boolean showPingGraph = false;
    private int graphDurationSeconds = 60;
    private String colorScheme = "AUTO";

    // Compatibility
    private boolean disableOnKryptonDetected = true;
    private boolean safeMode = false;

    // Mobile
    private boolean mobileAutoDetect = true;
    private boolean forceMobileProfile = false;
    private boolean batteryAware = true;
    private int batteryLowThresholdPercent = 20;
    private int batteryCriticalThresholdPercent = 10;
    private boolean memoryGuard = true;
    private float touchHudScale = 1.5f;
    private boolean showNetworkType = true;
    private boolean showBatteryStatus = true;
    private boolean showRamUsage = true;
    private boolean autoReconnectOnNetworkChange = true;
    private boolean aggressiveJitterBuffer = true;
    private boolean disableNativeLibraries = true;
    private String compressionCpuLimit = "LOW";
    private boolean reduceStatisticsHistory = true;

    private CesiumConfig() {
        // Defaults are set via field initializers above.
        // Profile is resolved after construction.
    }

    public static CesiumConfig load() {
        Path configPath = getConfigPath();
        CesiumConfig config = new CesiumConfig();

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                config.deserialize(root);
                LOGGER.info("[Cesium] Configuration loaded from {}", configPath);
            } catch (Exception e) {
                LOGGER.warn("[Cesium] Failed to load config, using defaults: {}", e.getMessage());
            }
        } else {
            LOGGER.info("[Cesium] No config file found, writing defaults to {}", configPath);
            config.save();
        }

        // Auto-detect mobile and switch profile if appropriate
        if (config.mobileAutoDetect && MobileDetector.isAndroid()) {
            LOGGER.info("[Cesium] Android detected — switching to MOBILE profile");
            config.profile = OptimizationProfile.MOBILE;
            config.applyMobileDefaults();
        } else if (config.profile == null) {
            config.profile = OptimizationProfile.BALANCED;
        }

        return config;
    }

    public void save() {
        Path configPath = getConfigPath();
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(serialize(), writer);
            }
        } catch (IOException e) {
            LOGGER.error("[Cesium] Failed to save config: {}", e.getMessage());
        }
    }

    private void deserialize(JsonObject root) {
        if (root.has("profile")) {
            try {
                profile = OptimizationProfile.valueOf(root.get("profile").getAsString());
            } catch (IllegalArgumentException e) {
                profile = OptimizationProfile.BALANCED;
            }
        }

        if (root.has("network")) {
            JsonObject net = root.getAsJsonObject("network");

            if (net.has("flush_consolidation")) {
                JsonObject fc = net.getAsJsonObject("flush_consolidation");
                flushConsolidationEnabled = getBoolean(fc, "enabled", flushConsolidationEnabled);
                explicitFlushAfterFlushes = getInt(fc, "explicit_flush_after_flushes", explicitFlushAfterFlushes);
            }
            if (net.has("compression")) {
                JsonObject comp = net.getAsJsonObject("compression");
                compressionEnabled = getBoolean(comp, "enabled", compressionEnabled);
                compressionAlgorithm = getString(comp, "algorithm", compressionAlgorithm);
                adaptiveCompression = getBoolean(comp, "adaptive", adaptiveCompression);
                dictionarySharing = getBoolean(comp, "dictionary_sharing", dictionarySharing);
                smallPacketThreshold = getInt(comp, "small_packet_threshold", smallPacketThreshold);
                preferNativeCompression = getBoolean(comp, "prefer_native", preferNativeCompression);
            }
            if (net.has("buffer")) {
                JsonObject buf = net.getAsJsonObject("buffer");
                useDirectBuffers = getBoolean(buf, "use_direct_buffers", useDirectBuffers);
                cacheTrimIntervalSeconds = getInt(buf, "cache_trim_interval_seconds", cacheTrimIntervalSeconds);
            }
            if (net.has("tcp")) {
                JsonObject tcp = net.getAsJsonObject("tcp");
                tcpNoDelay = getBoolean(tcp, "tcp_nodelay", tcpNoDelay);
                ipTos = getInt(tcp, "ip_tos", ipTos);
                autoTuneBuffers = getBoolean(tcp, "auto_tune_buffers", autoTuneBuffers);
                tcpKeepalive = getBoolean(tcp, "keepalive", tcpKeepalive);
            }
            if (net.has("native_transport")) {
                JsonObject nt = net.getAsJsonObject("native_transport");
                nativeTransportEnabled = getBoolean(nt, "enabled", nativeTransportEnabled);
                preferIoUring = getBoolean(nt, "prefer_io_uring", preferIoUring);
            }
        }

        if (root.has("dns")) {
            JsonObject dns = root.getAsJsonObject("dns");
            asyncDnsResolve = getBoolean(dns, "async_resolve", asyncDnsResolve);
            dnsCacheEnabled = getBoolean(dns, "cache_enabled", dnsCacheEnabled);
            dnsCacheMaxEntries = getInt(dns, "cache_max_entries", dnsCacheMaxEntries);
            dnsCacheMinTtlSeconds = getInt(dns, "cache_min_ttl_seconds", dnsCacheMinTtlSeconds);
            dnsDiskPersistence = getBoolean(dns, "disk_persistence", dnsDiskPersistence);
            happyEyeballs = getBoolean(dns, "happy_eyeballs", happyEyeballs);
            dnsOverHttps = getBoolean(dns, "dns_over_https", dnsOverHttps);
        }

        if (root.has("packets")) {
            JsonObject pkt = root.getAsJsonObject("packets");
            priorityQueueEnabled = getBoolean(pkt, "priority_queue", priorityQueueEnabled);
            if (pkt.has("coalescing")) {
                JsonObject coal = pkt.getAsJsonObject("coalescing");
                coalescingEnabled = getBoolean(coal, "enabled", coalescingEnabled);
                coalesceWindowMs = getInt(coal, "window_ms", coalesceWindowMs);
            }
            if (pkt.has("throttling")) {
                JsonObject thr = pkt.getAsJsonObject("throttling");
                throttlingEnabled = getBoolean(thr, "enabled", throttlingEnabled);
                minPositionIntervalMs = getInt(thr, "min_position_interval_ms", minPositionIntervalMs);
                deltaThreshold = getDouble(thr, "delta_threshold", deltaThreshold);
            }
        }

        if (root.has("prediction")) {
            JsonObject pred = root.getAsJsonObject("prediction");
            enhancedMovementPrediction = getBoolean(pred, "enhanced_movement_prediction", enhancedMovementPrediction);
            correctionSpeed = getDouble(pred, "correction_speed", correctionSpeed);
            errorThresholdBlocks = getDouble(pred, "error_threshold_blocks", errorThresholdBlocks);
            splineInterpolation = getBoolean(pred, "spline_interpolation", splineInterpolation);
            adaptiveInterpolationDelay = getBoolean(pred, "adaptive_interpolation_delay", adaptiveInterpolationDelay);
            if (pred.has("jitter_buffer")) {
                JsonObject jb = pred.getAsJsonObject("jitter_buffer");
                jitterBufferEnabled = getBoolean(jb, "enabled", jitterBufferEnabled);
                jitterBufferMode = getString(jb, "mode", jitterBufferMode);
                jitterBufferMinSize = getInt(jb, "min_size", jitterBufferMinSize);
                jitterBufferMaxSize = getInt(jb, "max_size", jitterBufferMaxSize);
            }
        }

        if (root.has("connection")) {
            JsonObject conn = root.getAsJsonObject("connection");
            autoReconnect = getBoolean(conn, "auto_reconnect", autoReconnect);
            reconnectMaxAttempts = getInt(conn, "reconnect_max_attempts", reconnectMaxAttempts);
            reconnectBackoffBaseMs = getInt(conn, "reconnect_backoff_base_ms", reconnectBackoffBaseMs);
            reconnectBackoffMaxMs = getInt(conn, "reconnect_backoff_max_ms", reconnectBackoffMaxMs);
            timeoutAdaptive = getBoolean(conn, "timeout_adaptive", timeoutAdaptive);
            keepalivePriority = getBoolean(conn, "keepalive_priority", keepalivePriority);
        }

        if (root.has("monitoring")) {
            JsonObject mon = root.getAsJsonObject("monitoring");
            collectStatistics = getBoolean(mon, "collect_statistics", collectStatistics);
            detailedPacketStats = getBoolean(mon, "detailed_packet_stats", detailedPacketStats);
            historyDurationMinutes = getInt(mon, "history_duration_minutes", historyDurationMinutes);
        }

        if (root.has("hud")) {
            JsonObject hud = root.getAsJsonObject("hud");
            hudEnabled = getBoolean(hud, "enabled", hudEnabled);
            hudMode = getString(hud, "mode", hudMode);
            hudPosition = getString(hud, "position", hudPosition);
            showPingGraph = getBoolean(hud, "show_ping_graph", showPingGraph);
            graphDurationSeconds = getInt(hud, "graph_duration_seconds", graphDurationSeconds);
            colorScheme = getString(hud, "color_scheme", colorScheme);
        }

        if (root.has("compatibility")) {
            JsonObject compat = root.getAsJsonObject("compatibility");
            disableOnKryptonDetected = getBoolean(compat, "disable_on_krypton_detected", disableOnKryptonDetected);
            safeMode = getBoolean(compat, "safe_mode", safeMode);
        }

        if (root.has("mobile")) {
            JsonObject mob = root.getAsJsonObject("mobile");
            mobileAutoDetect = getBoolean(mob, "auto_detect", mobileAutoDetect);
            forceMobileProfile = getBoolean(mob, "force_mobile_profile", forceMobileProfile);
            batteryAware = getBoolean(mob, "battery_aware", batteryAware);
            batteryLowThresholdPercent = getInt(mob, "battery_low_threshold_percent", batteryLowThresholdPercent);
            batteryCriticalThresholdPercent = getInt(mob, "battery_critical_threshold_percent", batteryCriticalThresholdPercent);
            memoryGuard = getBoolean(mob, "memory_guard", memoryGuard);
            touchHudScale = getFloat(mob, "touch_hud_scale", touchHudScale);
            showNetworkType = getBoolean(mob, "show_network_type", showNetworkType);
            showBatteryStatus = getBoolean(mob, "show_battery_status", showBatteryStatus);
            showRamUsage = getBoolean(mob, "show_ram_usage", showRamUsage);
            autoReconnectOnNetworkChange = getBoolean(mob, "auto_reconnect_on_network_change", autoReconnectOnNetworkChange);
            aggressiveJitterBuffer = getBoolean(mob, "aggressive_jitter_buffer", aggressiveJitterBuffer);
            disableNativeLibraries = getBoolean(mob, "disable_native_libraries", disableNativeLibraries);
            compressionCpuLimit = getString(mob, "compression_cpu_limit", compressionCpuLimit);
            reduceStatisticsHistory = getBoolean(mob, "reduce_statistics_history", reduceStatisticsHistory);
        }
    }

    private JsonObject serialize() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("profile", profile != null ? profile.name() : "BALANCED");

        JsonObject net = new JsonObject();
        JsonObject fc = new JsonObject();
        fc.addProperty("enabled", flushConsolidationEnabled);
        fc.addProperty("explicit_flush_after_flushes", explicitFlushAfterFlushes);
        net.add("flush_consolidation", fc);

        JsonObject comp = new JsonObject();
        comp.addProperty("enabled", compressionEnabled);
        comp.addProperty("algorithm", compressionAlgorithm);
        comp.addProperty("adaptive", adaptiveCompression);
        comp.addProperty("dictionary_sharing", dictionarySharing);
        comp.addProperty("small_packet_threshold", smallPacketThreshold);
        comp.addProperty("prefer_native", preferNativeCompression);
        net.add("compression", comp);

        JsonObject buf = new JsonObject();
        buf.addProperty("use_direct_buffers", useDirectBuffers);
        buf.addProperty("pool_arenas", "AUTO");
        buf.addProperty("cache_trim_interval_seconds", cacheTrimIntervalSeconds);
        net.add("buffer", buf);

        JsonObject tcp = new JsonObject();
        tcp.addProperty("tcp_nodelay", tcpNoDelay);
        tcp.addProperty("ip_tos", ipTos);
        tcp.addProperty("auto_tune_buffers", autoTuneBuffers);
        tcp.addProperty("keepalive", tcpKeepalive);
        net.add("tcp", tcp);

        JsonObject nt = new JsonObject();
        nt.addProperty("enabled", nativeTransportEnabled);
        nt.addProperty("prefer_io_uring", preferIoUring);
        net.add("native_transport", nt);

        root.add("network", net);

        JsonObject dns = new JsonObject();
        dns.addProperty("async_resolve", asyncDnsResolve);
        dns.addProperty("cache_enabled", dnsCacheEnabled);
        dns.addProperty("cache_max_entries", dnsCacheMaxEntries);
        dns.addProperty("cache_min_ttl_seconds", dnsCacheMinTtlSeconds);
        dns.addProperty("disk_persistence", dnsDiskPersistence);
        dns.addProperty("happy_eyeballs", happyEyeballs);
        dns.addProperty("custom_dns_servers", "[]");
        dns.addProperty("dns_over_https", dnsOverHttps);
        root.add("dns", dns);

        JsonObject packets = new JsonObject();
        packets.addProperty("priority_queue", priorityQueueEnabled);
        JsonObject coalescing = new JsonObject();
        coalescing.addProperty("enabled", coalescingEnabled);
        coalescing.addProperty("window_ms", coalesceWindowMs);
        packets.add("coalescing", coalescing);
        JsonObject throttling = new JsonObject();
        throttling.addProperty("enabled", throttlingEnabled);
        throttling.addProperty("min_position_interval_ms", minPositionIntervalMs);
        throttling.addProperty("delta_threshold", deltaThreshold);
        packets.add("throttling", throttling);
        root.add("packets", packets);

        JsonObject prediction = new JsonObject();
        prediction.addProperty("enhanced_movement_prediction", enhancedMovementPrediction);
        prediction.addProperty("correction_speed", correctionSpeed);
        prediction.addProperty("error_threshold_blocks", errorThresholdBlocks);
        prediction.addProperty("spline_interpolation", splineInterpolation);
        prediction.addProperty("adaptive_interpolation_delay", adaptiveInterpolationDelay);
        JsonObject jb = new JsonObject();
        jb.addProperty("enabled", jitterBufferEnabled);
        jb.addProperty("mode", jitterBufferMode);
        jb.addProperty("min_size", jitterBufferMinSize);
        jb.addProperty("max_size", jitterBufferMaxSize);
        prediction.add("jitter_buffer", jb);
        root.add("prediction", prediction);

        JsonObject connection = new JsonObject();
        connection.addProperty("auto_reconnect", autoReconnect);
        connection.addProperty("reconnect_max_attempts", reconnectMaxAttempts);
        connection.addProperty("reconnect_backoff_base_ms", reconnectBackoffBaseMs);
        connection.addProperty("reconnect_backoff_max_ms", reconnectBackoffMaxMs);
        connection.addProperty("timeout_adaptive", timeoutAdaptive);
        connection.addProperty("keepalive_priority", keepalivePriority);
        root.add("connection", connection);

        JsonObject monitoring = new JsonObject();
        monitoring.addProperty("collect_statistics", collectStatistics);
        monitoring.addProperty("detailed_packet_stats", detailedPacketStats);
        monitoring.addProperty("history_duration_minutes", historyDurationMinutes);
        root.add("monitoring", monitoring);

        JsonObject hud = new JsonObject();
        hud.addProperty("enabled", hudEnabled);
        hud.addProperty("mode", hudMode);
        hud.addProperty("position", hudPosition);
        hud.addProperty("show_ping_graph", showPingGraph);
        hud.addProperty("graph_duration_seconds", graphDurationSeconds);
        hud.addProperty("color_scheme", colorScheme);
        root.add("hud", hud);

        JsonObject compat = new JsonObject();
        compat.addProperty("disable_on_krypton_detected", disableOnKryptonDetected);
        compat.addProperty("safe_mode", safeMode);
        compat.add("disabled_mixins", new JsonArray());
        root.add("compatibility", compat);

        JsonObject mobile = new JsonObject();
        mobile.addProperty("auto_detect", mobileAutoDetect);
        mobile.addProperty("force_mobile_profile", forceMobileProfile);
        mobile.addProperty("battery_aware", batteryAware);
        mobile.addProperty("battery_low_threshold_percent", batteryLowThresholdPercent);
        mobile.addProperty("battery_critical_threshold_percent", batteryCriticalThresholdPercent);
        mobile.addProperty("memory_guard", memoryGuard);
        mobile.addProperty("touch_hud_scale", touchHudScale);
        mobile.addProperty("show_network_type", showNetworkType);
        mobile.addProperty("show_battery_status", showBatteryStatus);
        mobile.addProperty("show_ram_usage", showRamUsage);
        mobile.addProperty("auto_reconnect_on_network_change", autoReconnectOnNetworkChange);
        mobile.addProperty("aggressive_jitter_buffer", aggressiveJitterBuffer);
        mobile.addProperty("disable_native_libraries", disableNativeLibraries);
        mobile.addProperty("compression_cpu_limit", compressionCpuLimit);
        mobile.addProperty("reduce_statistics_history", reduceStatisticsHistory);
        root.add("mobile", mobile);

        return root;
    }

    private void applyMobileDefaults() {
        // Override settings not suited for mobile
        splineInterpolation = false;
        preferNativeCompression = false;
        nativeTransportEnabled = false;
        useDirectBuffers = false; // heap buffers are cheaper on Android
        aggressiveJitterBuffer = true;
        autoReconnect = true;
        disableNativeLibraries = true;
        reduceStatisticsHistory = true;
        touchHudScale = 1.5f;
        historyDurationMinutes = 1;
        graphDurationSeconds = 30;
    }

    private static Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("cesium-config.json");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean getBoolean(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        return obj.has(key) ? obj.get(key).getAsFloat() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public OptimizationProfile getProfile() { return profile; }
    public boolean isFlushConsolidationEnabled() { return flushConsolidationEnabled; }
    public int getExplicitFlushAfterFlushes() { return explicitFlushAfterFlushes; }
    public boolean isCompressionEnabled() { return compressionEnabled; }
    public String getCompressionAlgorithm() { return compressionAlgorithm; }
    public boolean isAdaptiveCompression() { return adaptiveCompression; }
    public boolean isDictionarySharing() { return dictionarySharing; }
    public int getSmallPacketThreshold() { return smallPacketThreshold; }
    public boolean isPreferNativeCompression() { return preferNativeCompression; }
    public boolean isUseDirectBuffers() { return useDirectBuffers; }
    public int getCacheTrimIntervalSeconds() { return cacheTrimIntervalSeconds; }
    public boolean isTcpNoDelay() { return tcpNoDelay; }
    public int getIpTos() { return ipTos; }
    public boolean isAutoTuneBuffers() { return autoTuneBuffers; }
    public boolean isTcpKeepalive() { return tcpKeepalive; }
    public boolean isNativeTransportEnabled() { return nativeTransportEnabled; }
    public boolean isPreferIoUring() { return preferIoUring; }
    public boolean isAsyncDnsResolve() { return asyncDnsResolve; }
    public boolean isDnsCacheEnabled() { return dnsCacheEnabled; }
    public int getDnsCacheMaxEntries() { return dnsCacheMaxEntries; }
    public int getDnsCacheMinTtlSeconds() { return dnsCacheMinTtlSeconds; }
    public boolean isDnsDiskPersistence() { return dnsDiskPersistence; }
    public boolean isHappyEyeballs() { return happyEyeballs; }
    public boolean isDnsOverHttps() { return dnsOverHttps; }
    public boolean isPriorityQueueEnabled() { return priorityQueueEnabled; }
    public boolean isCoalescingEnabled() { return coalescingEnabled; }
    public int getCoalesceWindowMs() { return coalesceWindowMs; }
    public boolean isThrottlingEnabled() { return throttlingEnabled; }
    public int getMinPositionIntervalMs() { return minPositionIntervalMs; }
    public double getDeltaThreshold() { return deltaThreshold; }
    public boolean isEnhancedMovementPrediction() { return enhancedMovementPrediction; }
    public double getCorrectionSpeed() { return correctionSpeed; }
    public double getErrorThresholdBlocks() { return errorThresholdBlocks; }
    public boolean isSplineInterpolation() { return splineInterpolation; }
    public boolean isAdaptiveInterpolationDelay() { return adaptiveInterpolationDelay; }
    public boolean isJitterBufferEnabled() { return jitterBufferEnabled; }
    public String getJitterBufferMode() { return jitterBufferMode; }
    public int getJitterBufferMinSize() { return jitterBufferMinSize; }
    public int getJitterBufferMaxSize() { return jitterBufferMaxSize; }
    public boolean isAutoReconnect() { return autoReconnect; }
    public int getReconnectMaxAttempts() { return reconnectMaxAttempts; }
    public int getReconnectBackoffBaseMs() { return reconnectBackoffBaseMs; }
    public int getReconnectBackoffMaxMs() { return reconnectBackoffMaxMs; }
    public boolean isTimeoutAdaptive() { return timeoutAdaptive; }
    public boolean isKeepalivePriority() { return keepalivePriority; }
    public boolean isCollectStatistics() { return collectStatistics; }
    public boolean isDetailedPacketStats() { return detailedPacketStats; }
    public int getHistoryDurationMinutes() { return historyDurationMinutes; }
    public boolean isHudEnabled() { return hudEnabled; }
    public String getHudMode() { return hudMode; }
    public String getHudPosition() { return hudPosition; }
    public boolean isShowPingGraph() { return showPingGraph; }
    public int getGraphDurationSeconds() { return graphDurationSeconds; }
    public String getColorScheme() { return colorScheme; }
    public boolean isDisableOnKryptonDetected() { return disableOnKryptonDetected; }
    public boolean isSafeMode() { return safeMode; }
    public boolean isMobileAutoDetect() { return mobileAutoDetect; }
    public boolean isForceMobileProfile() { return forceMobileProfile; }
    public boolean isBatteryAware() { return batteryAware; }
    public int getBatteryLowThresholdPercent() { return batteryLowThresholdPercent; }
    public int getBatteryCriticalThresholdPercent() { return batteryCriticalThresholdPercent; }
    public boolean isMemoryGuard() { return memoryGuard; }
    public float getTouchHudScale() { return touchHudScale; }
    public boolean isShowNetworkType() { return showNetworkType; }
    public boolean isShowBatteryStatus() { return showBatteryStatus; }
    public boolean isShowRamUsage() { return showRamUsage; }
    public boolean isAutoReconnectOnNetworkChange() { return autoReconnectOnNetworkChange; }
    public boolean isAggressiveJitterBuffer() { return aggressiveJitterBuffer; }
    public boolean isDisableNativeLibraries() { return disableNativeLibraries; }
    public String getCompressionCpuLimit() { return compressionCpuLimit; }
    public boolean isReduceStatisticsHistory() { return reduceStatisticsHistory; }
}
