package net.cesium.netty.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import net.cesium.CesiumConfig;
import net.cesium.core.PlatformDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

/**
 * Loads the best available Netty transport for the current platform.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Linux: io_uring (kernel 5.1+) → Epoll → NIO</li>
 *   <li>macOS: KQueue → NIO</li>
 *   <li>Windows / Android: NIO only</li>
 * </ol>
 */
public class NativeTransportLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumNativeTransport");

    public enum TransportType {
        IO_URING, EPOLL, KQUEUE, NIO
    }

    private final CesiumConfig config;
    private TransportType activeTransport = TransportType.NIO;
    private Class<? extends io.netty.channel.Channel> channelClass;
    private Class<? extends io.netty.channel.socket.DatagramChannel> datagramChannelClass;

    public NativeTransportLoader(CesiumConfig config) {
        this.config = config;
    }

    public void load() {
        if (!config.isNativeTransportEnabled() || !PlatformDetector.supportsNativeTransport()) {
            LOGGER.info("[Cesium] Native transport disabled or not supported on this platform.");
            loadNio();
            return;
        }

        if (PlatformDetector.isLinux()) {
            if (config.isPreferIoUring() && tryLoadIoUring()) return;
            if (tryLoadEpoll()) return;
        } else if (PlatformDetector.isMacOs()) {
            if (tryLoadKQueue()) return;
        }

        loadNio();
    }

    private boolean tryLoadIoUring() {
        try {
            Class<?> ioUringClass = Class.forName("io.netty.incubator.channel.uring.IOUring");
            boolean available = (boolean) ioUringClass.getMethod("isAvailable").invoke(null);
            if (available) {
                channelClass = Class.forName("io.netty.incubator.channel.uring.IOUringSocketChannel")
                        .asSubclass(io.netty.channel.Channel.class);
                datagramChannelClass = Class.forName("io.netty.incubator.channel.uring.IOUringDatagramChannel")
                        .asSubclass(io.netty.channel.socket.DatagramChannel.class);
                activeTransport = TransportType.IO_URING;
                LOGGER.info("[Cesium] Native transport: io_uring");
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] io_uring not available: {}", e.getMessage());
        }
        return false;
    }

    private boolean tryLoadEpoll() {
        try {
            Class<?> epollClass = Class.forName("io.netty.channel.epoll.Epoll");
            boolean available = (boolean) epollClass.getMethod("isAvailable").invoke(null);
            if (available) {
                channelClass = Class.forName("io.netty.channel.epoll.EpollSocketChannel")
                        .asSubclass(io.netty.channel.Channel.class);
                datagramChannelClass = Class.forName("io.netty.channel.epoll.EpollDatagramChannel")
                        .asSubclass(io.netty.channel.socket.DatagramChannel.class);
                activeTransport = TransportType.EPOLL;
                LOGGER.info("[Cesium] Native transport: Epoll");
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] Epoll not available: {}", e.getMessage());
        }
        return false;
    }

    private boolean tryLoadKQueue() {
        try {
            Class<?> kqueueClass = Class.forName("io.netty.channel.kqueue.KQueue");
            boolean available = (boolean) kqueueClass.getMethod("isAvailable").invoke(null);
            if (available) {
                channelClass = Class.forName("io.netty.channel.kqueue.KQueueSocketChannel")
                        .asSubclass(io.netty.channel.Channel.class);
                datagramChannelClass = Class.forName("io.netty.channel.kqueue.KQueueDatagramChannel")
                        .asSubclass(io.netty.channel.socket.DatagramChannel.class);
                activeTransport = TransportType.KQUEUE;
                LOGGER.info("[Cesium] Native transport: KQueue");
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[Cesium] KQueue not available: {}", e.getMessage());
        }
        return false;
    }

    private void loadNio() {
        channelClass = io.netty.channel.socket.nio.NioSocketChannel.class;
        datagramChannelClass = io.netty.channel.socket.nio.NioDatagramChannel.class;
        activeTransport = TransportType.NIO;
        LOGGER.info("[Cesium] Native transport: Java NIO (fallback)");
    }

    public EventLoopGroup createEventLoopGroup(int threads, ThreadFactory factory) {
        return switch (activeTransport) {
            case IO_URING -> createIoUringGroup(threads, factory);
            case EPOLL -> createEpollGroup(threads, factory);
            case KQUEUE -> createKQueueGroup(threads, factory);
            default -> new NioEventLoopGroup(threads, factory);
        };
    }

    private EventLoopGroup createIoUringGroup(int threads, ThreadFactory factory) {
        try {
            return (EventLoopGroup) Class.forName("io.netty.incubator.channel.uring.IOUringEventLoopGroup")
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(threads, factory);
        } catch (Exception e) {
            return new NioEventLoopGroup(threads, factory);
        }
    }

    private EventLoopGroup createEpollGroup(int threads, ThreadFactory factory) {
        try {
            return (EventLoopGroup) Class.forName("io.netty.channel.epoll.EpollEventLoopGroup")
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(threads, factory);
        } catch (Exception e) {
            return new NioEventLoopGroup(threads, factory);
        }
    }

    private EventLoopGroup createKQueueGroup(int threads, ThreadFactory factory) {
        try {
            return (EventLoopGroup) Class.forName("io.netty.channel.kqueue.KQueueEventLoopGroup")
                    .getConstructor(int.class, ThreadFactory.class)
                    .newInstance(threads, factory);
        } catch (Exception e) {
            return new NioEventLoopGroup(threads, factory);
        }
    }

    public TransportType getActiveTransport() { return activeTransport; }

    public Class<? extends io.netty.channel.Channel> getChannelClass() { return channelClass; }

    public Class<? extends io.netty.channel.socket.DatagramChannel> getDatagramChannelClass() {
        return datagramChannelClass;
    }
}
