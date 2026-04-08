package net.cesium.netty.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import net.cesium.CesiumConfig;
import net.cesium.CesiumMod;
import net.cesium.core.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies Cesium's TCP socket options to a newly connected {@link Channel}.
 *
 * <p>Called from {@code ChannelInitializerMixin} after the channel is created.
 * Designed for safe invocation — any unsupported option is caught and logged,
 * never propagated.
 */
public final class CesiumChannelInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumChannel");

    private CesiumChannelInitializer() {}

    /**
     * Applies all configured socket options to the given channel.
     *
     * @param channel the newly created (but not yet connected) channel
     */
    public static void configure(Channel channel) {
        CesiumConfig config = CesiumMod.getInstance().getConfig();

        // TCP_NODELAY — disable Nagle algorithm for low-latency sends.
        setOption(channel, ChannelOption.TCP_NODELAY, config.isTcpNoDelay());

        // IP_TOS — DSCP: 0x18 = Low Delay (0x10) | High Throughput (0x08)
        setOption(channel, ChannelOption.IP_TOS, config.getIpTos());

        // SO_KEEPALIVE — OS-level keepalive probes.
        setOption(channel, ChannelOption.SO_KEEPALIVE, config.isTcpKeepalive());

        // Adaptive buffer sizes based on bandwidth-delay product (BDP).
        if (config.isAutoTuneBuffers()) {
            tuneSocketBuffers(channel);
        }

        LOGGER.debug("[Cesium] Socket configured: TCP_NODELAY={} IP_TOS={} SO_KEEPALIVE={}",
                config.isTcpNoDelay(), config.getIpTos(), config.isTcpKeepalive());
    }

    /**
     * Tunes SO_RCVBUF and SO_SNDBUF based on a heuristic BDP estimate.
     *
     * <p>Without measured RTT/bandwidth at channel-creation time we use a
     * conservative default of 256 KB — large enough for high-bandwidth chunk
     * streaming but not wasteful on low-end devices.
     */
    private static void tuneSocketBuffers(Channel channel) {
        int bufferSize;
        if (PlatformDetector.isAndroid()) {
            // Android devices have limited RAM; keep buffers small.
            bufferSize = 64 * 1024;  // 64 KB
        } else {
            bufferSize = 256 * 1024; // 256 KB
        }
        setOption(channel, ChannelOption.SO_RCVBUF, bufferSize);
        setOption(channel, ChannelOption.SO_SNDBUF, bufferSize);
    }

    private static <T> void setOption(Channel channel, ChannelOption<T> option, T value) {
        try {
            channel.config().setOption(option, value);
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Could not set {} on channel: {}", option, e.getMessage());
        }
    }
}
