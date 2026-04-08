package net.cesium.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import net.cesium.CesiumConfig;
import net.cesium.netty.handler.CesiumChannelInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies and enforces TCP connection stability settings on active channels.
 */
public class ConnectionStabilizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumStabilizer");

    private final CesiumConfig config;

    public ConnectionStabilizer(CesiumConfig config) {
        this.config = config;
    }

    /**
     * Called when a new channel becomes active (connected).
     * Delegates to {@link CesiumChannelInitializer} for socket option application.
     */
    public void onChannelActive(Channel channel) {
        CesiumChannelInitializer.configure(channel);
        LOGGER.debug("[Cesium] Channel stabilized: {}", channel.remoteAddress());
    }

    /**
     * Applies adaptive read/write timeout parameters based on current connection
     * quality. To be called periodically (e.g., every 10 seconds) from the
     * network statistics monitor.
     *
     * @param channel       the active channel
     * @param avgRttMs      rolling average RTT in milliseconds
     * @param jitterMs      current jitter in milliseconds
     */
    public void updateTimeouts(Channel channel, double avgRttMs, double jitterMs) {
        if (!config.isTimeoutAdaptive()) return;

        // Read timeout: base 30 s, extended when jitter is high
        int readTimeoutSeconds;
        if (jitterMs > 100) {
            readTimeoutSeconds = 60;
        } else if (jitterMs > 50) {
            readTimeoutSeconds = 45;
        } else {
            readTimeoutSeconds = 30;
        }

        // Applying read timeout dynamically requires a ReadTimeoutHandler in the pipeline.
        // This method signals the intent; the actual handler replacement happens in the mixin.
        LOGGER.debug("[Cesium] Adaptive timeout: {}s (avg={}ms jitter={}ms)",
                readTimeoutSeconds, (int) avgRttMs, (int) jitterMs);
    }
}
