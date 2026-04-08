package net.cesium.prediction;

import net.cesium.CesiumConfig;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Adaptive jitter buffer that absorbs network jitter by temporarily holding
 * incoming data before delivering it to the game.
 *
 * <p>The buffer size auto-adjusts based on observed jitter:
 * <ul>
 *   <li>Low jitter (< 10 ms variance): 1-packet buffer</li>
 *   <li>Medium jitter (10–50 ms): 2-packet buffer</li>
 *   <li>High jitter (> 50 ms): up to {@code maxSize} packets</li>
 * </ul>
 *
 * <p>This does not reduce measured ping — it reduces ping variance, making
 * the connection feel more stable.
 */
public class JitterBuffer {

    private final int minSize;
    private final int maxSize;
    private int currentTarget;

    private final Deque<Object> buffer = new ArrayDeque<>();

    // Rolling jitter estimation
    private double lastRtt = 0;
    private double jitterEstimate = 0;
    private static final double JITTER_ALPHA = 0.1; // EWMA smoothing factor

    public JitterBuffer(CesiumConfig config) {
        this.minSize = config.getJitterBufferMinSize();
        this.maxSize = config.getJitterBufferMaxSize();
        this.currentTarget = minSize;
    }

    /**
     * Updates the jitter estimate using an RTT sample and adjusts buffer target.
     *
     * @param rttMs current round-trip time in milliseconds
     */
    public void updateJitter(double rttMs) {
        if (lastRtt != 0) {
            double delta = Math.abs(rttMs - lastRtt);
            // EWMA: exponentially weighted moving average
            jitterEstimate = (1 - JITTER_ALPHA) * jitterEstimate + JITTER_ALPHA * delta;
        }
        lastRtt = rttMs;
        adaptBufferSize();
    }

    /** Adds a unit of data (packet, entity update, etc.) to the buffer. */
    public void enqueue(Object item) {
        buffer.addLast(item);
    }

    /**
     * Returns the next item from the buffer if the buffer is sufficiently
     * filled, or {@code null} if the buffer should keep accumulating.
     */
    public Object poll() {
        if (buffer.size() > currentTarget) {
            return buffer.pollFirst();
        }
        return null;
    }

    /** Forces draining the entire buffer (e.g., on disconnect or flush). */
    public Object drain() {
        return buffer.pollFirst();
    }

    public boolean isEmpty() { return buffer.isEmpty(); }
    public int size() { return buffer.size(); }
    public double getJitterEstimate() { return jitterEstimate; }

    public void clear() {
        buffer.clear();
        jitterEstimate = 0;
        lastRtt = 0;
    }

    private void adaptBufferSize() {
        if (jitterEstimate < 10) {
            currentTarget = minSize;
        } else if (jitterEstimate < 50) {
            currentTarget = Math.min(minSize + 1, maxSize);
        } else {
            currentTarget = maxSize;
        }
    }
}
