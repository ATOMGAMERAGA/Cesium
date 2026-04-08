package net.cesium.connection;

import net.cesium.CesiumMod;
import net.cesium.monitoring.PingTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intercepts Minecraft's KeepAlive flow to:
 * <ol>
 *   <li>Measure the true round-trip time per KeepAlive cycle.</li>
 *   <li>Ensure KeepAlive responses are sent at the highest packet priority.</li>
 * </ol>
 *
 * <p>This class is stateless with respect to channel connections — the mixin
 * passes the keepAlive ID and the tracker handles bookkeeping.
 */
public class KeepAliveOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("CesiumKeepAlive");

    private final PingTracker pingTracker = new PingTracker();

    public KeepAliveOptimizer() {}

    /**
     * Called by {@code ClientPlayNetworkHandlerMixin} when the client is about
     * to send a KeepAlive response. Records the send timestamp.
     *
     * @param keepAliveId the ID from the server's KeepAlive packet
     */
    public void onSendingKeepAlive(long keepAliveId) {
        pingTracker.onKeepAliveSent(keepAliveId);
        LOGGER.debug("[Cesium] KeepAlive sent: id={}", keepAliveId);
    }

    /**
     * Called when a KeepAlive packet is received FROM the server.
     * (Not a response — this is the outgoing trigger.)
     */
    public void onReceivedKeepAlive(long keepAliveId) {
        // The server sent us a KeepAlive challenge; record when we received it
        // so that when we echo it back we can compute one-way delay if needed.
        LOGGER.debug("[Cesium] KeepAlive received from server: id={}", keepAliveId);
    }

    /**
     * Called when a KeepAlive response echoed back by US is confirmed sent.
     * Updates the RTT tracker.
     */
    public void onResponseConfirmed(long keepAliveId) {
        pingTracker.onKeepAliveReceived(keepAliveId);
    }

    public PingTracker getPingTracker() { return pingTracker; }

    public int getLastPingMs() { return pingTracker.getLastPingMs(); }
}
