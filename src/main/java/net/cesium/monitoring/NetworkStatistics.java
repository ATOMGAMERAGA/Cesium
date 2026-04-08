package net.cesium.monitoring;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe collector for real-time network statistics.
 *
 * <p>All writes come from the Netty I/O thread; reads come from the render
 * thread (HUD). {@link LongAdder} is used for high-frequency counters to
 * avoid contention.
 */
public class NetworkStatistics {

    /** RTT sample stored with a timestamp for time-window filtering. */
    public record RttSample(long timestampMs, int rttMs) {}

    private final int historyDurationMinutes;

    // ── RTT / Ping ─────────────────────────────────────────────────────────
    private final Deque<RttSample> rttHistory = new ConcurrentLinkedDeque<>();
    private volatile int lastRttMs = 0;

    // ── Traffic ────────────────────────────────────────────────────────────
    private final LongAdder bytesSent = new LongAdder();
    private final LongAdder bytesReceived = new LongAdder();
    private final LongAdder packetsSent = new LongAdder();
    private final LongAdder packetsReceived = new LongAdder();

    // ── Compression ───────────────────────────────────────────────────────
    private final LongAdder bytesBeforeCompression = new LongAdder();
    private final LongAdder bytesAfterCompression = new LongAdder();

    // ── Flush consolidation ───────────────────────────────────────────────
    private final LongAdder flushesBatched = new LongAdder();
    private final LongAdder flushesImmediate = new LongAdder();

    public NetworkStatistics(int historyDurationMinutes, boolean detailedPacketStats) {
        this.historyDurationMinutes = historyDurationMinutes;
    }

    // ── Record methods (called from Netty I/O thread) ─────────────────────

    public void recordRtt(int rttMs) {
        lastRttMs = rttMs;
        rttHistory.addLast(new RttSample(System.currentTimeMillis(), rttMs));
        pruneHistory();
    }

    public void recordBytesSent(int bytes) {
        bytesSent.add(bytes);
        packetsSent.increment();
    }

    public void recordBytesReceived(int bytes) {
        bytesReceived.add(bytes);
        packetsReceived.increment();
    }

    public void recordCompression(int originalBytes, int compressedBytes) {
        bytesBeforeCompression.add(originalBytes);
        bytesAfterCompression.add(compressedBytes);
    }

    public void recordBatchedFlush() { flushesBatched.increment(); }
    public void recordImmediateFlush() { flushesImmediate.increment(); }

    // ── Query methods (called from render thread) ─────────────────────────

    public int getLastRttMs() { return lastRttMs; }

    public int getMinRttMs() {
        return rttHistory.stream().mapToInt(RttSample::rttMs).min().orElse(0);
    }

    public int getMaxRttMs() {
        return rttHistory.stream().mapToInt(RttSample::rttMs).max().orElse(0);
    }

    public double getAvgRttMs() {
        return rttHistory.stream().mapToInt(RttSample::rttMs).average().orElse(0);
    }

    /** Jitter: standard deviation of RTT samples. */
    public double getJitterMs() {
        List<RttSample> samples = new ArrayList<>(rttHistory);
        if (samples.size() < 2) return 0;
        double avg = samples.stream().mapToInt(RttSample::rttMs).average().orElse(0);
        double variance = samples.stream()
                .mapToDouble(s -> Math.pow(s.rttMs() - avg, 2))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    public long getTotalBytesSent() { return bytesSent.sum(); }
    public long getTotalBytesReceived() { return bytesReceived.sum(); }
    public long getTotalPacketsSent() { return packetsSent.sum(); }
    public long getTotalPacketsReceived() { return packetsReceived.sum(); }

    /** Compression ratio: bytes saved / bytes before compression (0–1). */
    public double getCompressionRatio() {
        long before = bytesBeforeCompression.sum();
        long after = bytesAfterCompression.sum();
        if (before == 0) return 0;
        return 1.0 - (double) after / before;
    }

    public long getFlushesBatched() { return flushesBatched.sum(); }
    public long getFlushesImmediate() { return flushesImmediate.sum(); }

    /** Returns a snapshot of the RTT history (newest last). */
    public List<RttSample> getRttHistory() {
        return new ArrayList<>(rttHistory);
    }

    public void reset() {
        rttHistory.clear();
        lastRttMs = 0;
        bytesSent.reset();
        bytesReceived.reset();
        packetsSent.reset();
        packetsReceived.reset();
        bytesBeforeCompression.reset();
        bytesAfterCompression.reset();
        flushesBatched.reset();
        flushesImmediate.reset();
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private void pruneHistory() {
        long cutoff = System.currentTimeMillis() - (long) historyDurationMinutes * 60_000;
        while (!rttHistory.isEmpty() && rttHistory.peekFirst().timestampMs() < cutoff) {
            rttHistory.pollFirst();
        }
    }
}
