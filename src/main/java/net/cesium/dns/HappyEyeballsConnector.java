package net.cesium.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * RFC 8305 "Happy Eyeballs v2" connection racer.
 *
 * <p>Races IPv6 and IPv4 connection attempts simultaneously, giving IPv6
 * a 250 ms head start. The first successfully connected channel "wins";
 * the other is closed immediately.
 *
 * <p>This eliminates the multi-second timeout penalty that occurs when a
 * server advertises both IPv4 and IPv6 but one of them is unreachable.
 */
public class HappyEyeballsConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumHappyEyeballs");
    /** RFC 8305 §8: delay before starting IPv4 attempts after IPv6. */
    private static final int IPV6_PREFERENCE_DELAY_MS = 250;

    private HappyEyeballsConnector() {}

    /**
     * Races connections to all resolved addresses using Happy Eyeballs.
     *
     * @param bootstrap  configured but not yet connected Netty {@link Bootstrap}
     * @param addresses  resolved IP addresses for the target host
     * @param port       target port
     * @return the winning (connected) channel
     * @throws Exception if all attempts fail
     */
    public static Channel connect(Bootstrap bootstrap, InetAddress[] addresses, int port)
            throws Exception {
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException("No addresses to connect to");
        }

        List<InetAddress> ipv6 = new ArrayList<>();
        List<InetAddress> ipv4 = new ArrayList<>();

        for (InetAddress addr : addresses) {
            if (addr instanceof Inet6Address) ipv6.add(addr);
            else if (addr instanceof Inet4Address) ipv4.add(addr);
        }

        if (ipv6.isEmpty()) {
            // No IPv6 — connect directly to the first IPv4 address.
            return connectDirect(bootstrap, ipv4.get(0), port);
        }
        if (ipv4.isEmpty()) {
            return connectDirect(bootstrap, ipv6.get(0), port);
        }

        return race(bootstrap, ipv6, ipv4, port);
    }

    /** Races IPv6 addresses (with head start) against IPv4 addresses. */
    private static Channel race(Bootstrap bootstrap,
                                List<InetAddress> ipv6,
                                List<InetAddress> ipv4,
                                int port) throws Exception {
        ExecutorService scheduler = Executors.newVirtualThreadPerTaskExecutor();
        CompletableFuture<Channel> winner = new CompletableFuture<>();

        // Start IPv6 immediately.
        for (InetAddress addr : ipv6) {
            scheduler.submit(() -> attempt(bootstrap, addr, port, winner));
        }

        // Start IPv4 after the preference delay.
        scheduler.submit(() -> {
            try {
                Thread.sleep(IPV6_PREFERENCE_DELAY_MS);
                if (!winner.isDone()) {
                    for (InetAddress addr : ipv4) {
                        attempt(bootstrap, addr, port, winner);
                    }
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });

        try {
            Channel ch = winner.get(10, TimeUnit.SECONDS);
            LOGGER.debug("[Cesium] Happy Eyeballs: connected via {}", ch.remoteAddress());
            return ch;
        } catch (TimeoutException e) {
            throw new Exception("Happy Eyeballs: all connection attempts timed out");
        } finally {
            scheduler.shutdownNow();
        }
    }

    private static void attempt(Bootstrap bootstrap, InetAddress addr, int port,
                                CompletableFuture<Channel> winner) {
        try {
            ChannelFuture future = bootstrap.connect(addr, port).sync();
            if (future.isSuccess()) {
                if (!winner.complete(future.channel())) {
                    // Another attempt already won — close this channel.
                    future.channel().close();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Connection attempt to {}:{} failed: {}", addr, port, e.getMessage());
        }
    }

    private static Channel connectDirect(Bootstrap bootstrap, InetAddress addr, int port) throws Exception {
        return bootstrap.connect(addr, port).sync().channel();
    }
}
