package net.cesium.hud;

import net.cesium.monitoring.NetworkStatistics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Renders a real-time RTT sparkline graph in the Cesium HUD.
 */
@Environment(EnvType.CLIENT)
public class PingGraph {

    private static final int GRAPH_WIDTH  = 120;
    private static final int GRAPH_HEIGHT = 40;
    private static final int MAX_VALUE_MS = 500; // clamp display at 500 ms

    private static final int COLOR_BG   = 0x80000000;
    private static final int COLOR_LINE = 0xFF55FF55;
    private static final int COLOR_HIGH = 0xFFFF5555;

    private final int durationSeconds;
    private final Deque<Integer> samples = new ArrayDeque<>();

    public PingGraph(int durationSeconds) {
        this.durationSeconds = Math.max(durationSeconds, 10);
    }

    /** Updates the graph with the latest RTT history from statistics. */
    public void update(NetworkStatistics stats) {
        var history = stats.getRttHistory();
        samples.clear();
        for (var sample : history) {
            samples.addLast(sample.rttMs());
        }
    }

    /** Renders the graph at the given screen coordinates. */
    public void render(DrawContext context, int x, int y) {
        // Background
        context.fill(x, y, x + GRAPH_WIDTH, y + GRAPH_HEIGHT, COLOR_BG);

        if (samples.isEmpty()) return;

        Integer[] values = samples.toArray(new Integer[0]);
        int count = values.length;
        if (count < 2) return;

        float xStep = (float) GRAPH_WIDTH / (count - 1);

        for (int i = 1; i < count; i++) {
            int v0 = Math.min(values[i - 1], MAX_VALUE_MS);
            int v1 = Math.min(values[i], MAX_VALUE_MS);

            int x0 = x + Math.round((i - 1) * xStep);
            int x1 = x + Math.round(i * xStep);
            int y0 = y + GRAPH_HEIGHT - (v0 * GRAPH_HEIGHT / MAX_VALUE_MS);
            int y1 = y + GRAPH_HEIGHT - (v1 * GRAPH_HEIGHT / MAX_VALUE_MS);

            int color = (v1 > 200) ? COLOR_HIGH : COLOR_LINE;
            drawLine(context, x0, y0, x1, y1, color);
        }
    }

    /** Bresenham line between two pixels. */
    private static void drawLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            ctx.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }
}
