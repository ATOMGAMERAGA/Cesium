package net.cesium.core;

import net.cesium.CesiumConfig;
import net.cesium.connection.ConnectionStabilizer;
import net.cesium.connection.TimeoutOptimizer;
import net.cesium.dns.AsyncDnsResolver;
import net.cesium.dns.DnsCache;
import net.cesium.monitoring.BandwidthMonitor;
import net.cesium.monitoring.NetworkStatistics;
import net.cesium.monitoring.PacketAnalyzer;
import net.cesium.netty.allocator.CesiumByteBufAllocator;
import net.cesium.netty.transport.NativeCompressionLoader;
import net.cesium.netty.transport.NativeTransportLoader;
import net.cesium.packet.PacketCoalescer;
import net.cesium.packet.PacketPriorityQueue;
import net.cesium.prediction.JitterBuffer;
import net.cesium.prediction.LatencyCompensator;
import net.cesium.prediction.MovementPredictor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central orchestrator for all Cesium network optimizations.
 */
public class CesiumNetworkEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumEngine");

    private final CesiumConfig config;

    private NativeTransportLoader nativeTransportLoader;
    private NativeCompressionLoader nativeCompressionLoader;
    private CesiumByteBufAllocator byteBufAllocator;
    private DnsCache dnsCache;
    private AsyncDnsResolver dnsResolver;
    private NetworkStatistics networkStatistics;
    private BandwidthMonitor uploadMonitor;
    private BandwidthMonitor downloadMonitor;
    private PacketAnalyzer packetAnalyzer;
    private PacketPriorityQueue packetPriorityQueue;
    private PacketCoalescer packetCoalescer;
    private LatencyCompensator latencyCompensator;
    private MovementPredictor movementPredictor;
    private JitterBuffer jitterBuffer;
    private ConnectionStabilizer connectionStabilizer;
    private TimeoutOptimizer timeoutOptimizer;

    private boolean initialized = false;

    public CesiumNetworkEngine(CesiumConfig config) {
        this.config = config;
    }

    public void initialize() {
        if (initialized) return;
        LOGGER.info("[Cesium] Starting network engine (profile={})", config.getProfile());

        initNativeTransport();
        initNativeCompression();
        initAllocator();
        initDns();
        initStatistics();
        initPacketPipeline();
        initPrediction();
        initConnectionManagement();

        initialized = true;
        LOGGER.info("[Cesium] Network engine ready.");
    }

    private void initNativeTransport() {
        nativeTransportLoader = new NativeTransportLoader(config);
        nativeTransportLoader.load();
    }

    private void initNativeCompression() {
        nativeCompressionLoader = new NativeCompressionLoader(config);
        nativeCompressionLoader.load();
    }

    private void initAllocator() {
        byteBufAllocator = new CesiumByteBufAllocator(config);
        byteBufAllocator.initialize();
    }

    private void initDns() {
        if (config.isDnsCacheEnabled()) {
            dnsCache = new DnsCache(
                    config.getDnsCacheMaxEntries(),
                    config.getDnsCacheMinTtlSeconds(),
                    config.isDnsDiskPersistence()
            );
            dnsCache.loadFromDisk();
        }
        if (config.isAsyncDnsResolve()) {
            dnsResolver = new AsyncDnsResolver(dnsCache, config);
        }
    }

    private void initStatistics() {
        if (config.isCollectStatistics()) {
            networkStatistics = new NetworkStatistics(
                    config.getHistoryDurationMinutes(),
                    config.isDetailedPacketStats()
            );
            uploadMonitor = new BandwidthMonitor();
            downloadMonitor = new BandwidthMonitor();
        }
        if (config.isDetailedPacketStats()) {
            packetAnalyzer = new PacketAnalyzer();
        }
    }

    private void initPacketPipeline() {
        if (config.isPriorityQueueEnabled()) {
            packetPriorityQueue = new PacketPriorityQueue();
        }
        if (config.isCoalescingEnabled()) {
            packetCoalescer = new PacketCoalescer(config);
        }
    }

    private void initPrediction() {
        latencyCompensator = new LatencyCompensator();
        if (config.isEnhancedMovementPrediction()) {
            movementPredictor = new MovementPredictor(config);
        }
        if (config.isJitterBufferEnabled()) {
            jitterBuffer = new JitterBuffer(config);
        }
    }

    private void initConnectionManagement() {
        connectionStabilizer = new ConnectionStabilizer(config);
        timeoutOptimizer = new TimeoutOptimizer(config);
    }

    public void shutdown() {
        if (dnsCache != null) dnsCache.saveToDisk();
        if (dnsResolver != null) dnsResolver.shutdown();
        if (byteBufAllocator != null) byteBufAllocator.shutdown();
        LOGGER.info("[Cesium] Network engine shut down.");
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public CesiumConfig getConfig() { return config; }
    public NativeTransportLoader getNativeTransportLoader() { return nativeTransportLoader; }
    public NativeCompressionLoader getNativeCompressionLoader() { return nativeCompressionLoader; }
    public CesiumByteBufAllocator getByteBufAllocator() { return byteBufAllocator; }
    public DnsCache getDnsCache() { return dnsCache; }
    public AsyncDnsResolver getDnsResolver() { return dnsResolver; }
    public NetworkStatistics getNetworkStatistics() { return networkStatistics; }
    public BandwidthMonitor getUploadMonitor() { return uploadMonitor; }
    public BandwidthMonitor getDownloadMonitor() { return downloadMonitor; }
    public PacketAnalyzer getPacketAnalyzer() { return packetAnalyzer; }
    public PacketPriorityQueue getPacketPriorityQueue() { return packetPriorityQueue; }
    public PacketCoalescer getPacketCoalescer() { return packetCoalescer; }
    public LatencyCompensator getLatencyCompensator() { return latencyCompensator; }
    public MovementPredictor getMovementPredictor() { return movementPredictor; }
    public JitterBuffer getJitterBuffer() { return jitterBuffer; }
    public ConnectionStabilizer getConnectionStabilizer() { return connectionStabilizer; }
    public TimeoutOptimizer getTimeoutOptimizer() { return timeoutOptimizer; }
    public boolean isInitialized() { return initialized; }
}
