package net.cesium.connection;

import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Manages adaptive read/write timeout parameters for the Netty pipeline.
 *
 * <p>Default Minecraft read timeout is 30 seconds (hardcoded). Cesium extends
 * this on high-jitter connections to prevent false disconnections while
 * shortening it on stable connections for faster error detection.
 */
public class TimeoutOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumTimeout");

    private static final int STABLE_TIMEOUT_S  = 30;
    private static final int MEDIUM_TIMEOUT_S  = 45;
    private static final int RELAXED_TIMEOUT_S = 60;

    private final boolean adaptive;

    public TimeoutOptimizer(CesiumConfig config) {
        this.adaptive = config.isTimeoutAdaptive();
    }

    /**
     * Applies (or replaces) the {@link ReadTimeoutHandler} on the given channel
     * based on the observed jitter.
     *
     * @param channel  the active channel
     * @param jitterMs current jitter in milliseconds
     */
    public void applyReadTimeout(Channel channel, double jitterMs) {
        if (!adaptive) return;
        int seconds = selectTimeout(jitterMs);
        replaceHandler(channel, seconds);
    }

    private static int selectTimeout(double jitterMs) {
        if (jitterMs > 100) return RELAXED_TIMEOUT_S;
        if (jitterMs > 40)  return MEDIUM_TIMEOUT_S;
        return STABLE_TIMEOUT_S;
    }

    private static void replaceHandler(Channel channel, int seconds) {
        try {
            var pipeline = channel.pipeline();
            if (pipeline.get("timeout") != null) {
                pipeline.replace("timeout", "timeout",
                        new ReadTimeoutHandler(seconds, TimeUnit.SECONDS));
                LOGGER.debug("[Cesium] Read timeout set to {}s", seconds);
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Could not update read timeout: {}", e.getMessage());
        }
    }
}
