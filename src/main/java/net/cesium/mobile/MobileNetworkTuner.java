package net.cesium.mobile;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies Android-specific TCP socket tuning to active channels.
 *
 * <p>Android's default TCP stack behaves differently from desktop Linux:
 * <ul>
 *   <li>Buffer sizes may be smaller by default.</li>
 *   <li>TCP_NODELAY is not always honoured without explicit setting.</li>
 *   <li>Some socket options (e.g. IP_TOS) are silently ignored.</li>
 * </ul>
 *
 * <p>This class mirrors {@link net.cesium.netty.handler.CesiumChannelInitializer}
 * but applies only the subset of options known to work on Android.
 */
public class MobileNetworkTuner {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumMobileTuner");

    private final CesiumConfig config;

    public MobileNetworkTuner(CesiumConfig config) {
        this.config = config;
    }

    /**
     * Applies mobile-compatible socket options to the channel.
     * Called from {@link net.cesium.connection.ConnectionStabilizer#onChannelActive}.
     */
    public void tune(Channel channel) {
        // TCP_NODELAY is the most impactful — always apply.
        setOption(channel, ChannelOption.TCP_NODELAY, true);

        // SO_KEEPALIVE — helps detect dropped connections on mobile networks.
        setOption(channel, ChannelOption.SO_KEEPALIVE, true);

        // Conservative buffers for Android (avoid large heap allocations).
        int bufSize = 64 * 1024; // 64 KB
        setOption(channel, ChannelOption.SO_RCVBUF, bufSize);
        setOption(channel, ChannelOption.SO_SNDBUF, bufSize);

        // IP_TOS — may be silently ignored on Android but doesn't hurt.
        setOption(channel, ChannelOption.IP_TOS, config.getIpTos());

        LOGGER.debug("[Cesium] Mobile network tuning applied to {}", channel.remoteAddress());
    }

    private static <T> void setOption(Channel channel, ChannelOption<T> option, T value) {
        try {
            channel.config().setOption(option, value);
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Socket option {} ignored on Android: {}", option, e.getMessage());
        }
    }
}
