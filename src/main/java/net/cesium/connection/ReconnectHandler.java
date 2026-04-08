package net.cesium.connection;

import net.cesium.CesiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Handles automatic reconnection with exponential back-off.
 *
 * <p>Back-off schedule (default config):
 * <pre>
 *   Attempt 1: 1 s
 *   Attempt 2: 2 s
 *   Attempt 3: 4 s
 *   Attempt 4: 8 s
 *   Attempt 5: 16 s → capped at 30 s
 * </pre>
 */
public class ReconnectHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumReconnect");

    private final CesiumConfig config;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger attempts = new AtomicInteger(0);

    private volatile boolean active = false;
    private volatile String lastHost;
    private volatile int lastPort;
    private Supplier<Boolean> reconnectAction; // returns true on success

    public ReconnectHandler(CesiumConfig config) {
        this.config = config;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "cesium-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initiates the reconnect sequence.
     *
     * @param host            server hostname
     * @param port            server port
     * @param reconnectAction action to perform on each attempt; returns {@code true} on success
     */
    public void startReconnect(String host, int port, Supplier<Boolean> reconnectAction) {
        if (!config.isAutoReconnect()) return;

        this.lastHost = host;
        this.lastPort = port;
        this.reconnectAction = reconnectAction;
        this.active = true;
        this.attempts.set(0);

        scheduleNextAttempt();
    }

    public void cancel() {
        active = false;
        attempts.set(0);
    }

    public boolean isActive() { return active; }
    public int getAttemptCount() { return attempts.get(); }

    private void scheduleNextAttempt() {
        if (!active) return;

        int attempt = attempts.get();
        if (attempt >= config.getReconnectMaxAttempts()) {
            LOGGER.warn("[Cesium] Reconnect failed after {} attempts, giving up.", attempt);
            active = false;
            return;
        }

        long delayMs = computeDelay(attempt);
        LOGGER.info("[Cesium] Reconnecting in {}ms (attempt {}/{})",
                delayMs, attempt + 1, config.getReconnectMaxAttempts());

        scheduler.schedule(() -> {
            if (!active) return;
            attempts.incrementAndGet();
            boolean success = reconnectAction.get();
            if (success) {
                LOGGER.info("[Cesium] Reconnect successful after {} attempt(s)", attempts.get());
                active = false;
            } else {
                scheduleNextAttempt();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private long computeDelay(int attempt) {
        long base = config.getReconnectBackoffBaseMs();
        long max = config.getReconnectBackoffMaxMs();
        return Math.min(base * (1L << attempt), max);
    }

    public void shutdown() {
        active = false;
        scheduler.shutdownNow();
    }
}
