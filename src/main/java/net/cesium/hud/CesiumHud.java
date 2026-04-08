package net.cesium.hud;

import net.cesium.CesiumMod;
import net.cesium.mobile.MobileDetector;
import net.cesium.monitoring.NetworkStatistics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Renders the Cesium network statistics HUD overlay.
 *
 * <p>Three display modes:
 * <ul>
 *   <li><b>MINIMAL</b>: single-line ping + jitter (default)</li>
 *   <li><b>DETAILED</b>: multi-line network panel</li>
 *   <li><b>GRAPH</b>: includes a real-time RTT sparkline</li>
 * </ul>
 *
 * <p>Mobile: font is scaled up to 1.5× for readability on small screens,
 * and additional fields (RAM, battery, network type) are shown.
 */
@Environment(EnvType.CLIENT)
public class CesiumHud implements HudRenderCallback {

    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_GREEN = 0x55FF55;
    private static final int COLOR_YELLOW = 0xFFFF55;
    private static final int COLOR_RED = 0xFF5555;
    private static final int PADDING = 4;

    private boolean visible;
    private final String mode;
    private final PingGraph pingGraph;
    private final boolean isMobile;
    private final float hudScale;

    public CesiumHud() {
        var config = CesiumMod.getInstance().getConfig();
        this.visible = config.isHudEnabled();
        this.mode = config.getHudMode();
        this.isMobile = MobileDetector.isAndroid();
        this.hudScale = isMobile ? config.getTouchHudScale() : 1.0f;

        if ("GRAPH".equals(mode) || "DETAILED".equals(mode)) {
            this.pingGraph = new PingGraph(config.getGraphDurationSeconds());
        } else {
            this.pingGraph = null;
        }
    }

    public static void register(CesiumHud hud) {
        HudRenderCallback.EVENT.register(hud);
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (!visible) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getDebugHud().shouldShowDebugHud()) return; // hide when F3 is open
        if (client.currentScreen != null) return;

        NetworkStatistics stats = getStats();
        if (stats == null) return;

        TextRenderer textRenderer = client.textRenderer;
        int x = PADDING;
        int y = PADDING;

        switch (mode) {
            case "DETAILED" -> renderDetailed(context, textRenderer, stats, x, y);
            case "GRAPH" -> renderWithGraph(context, textRenderer, stats, x, y, pingGraph);
            default -> renderMinimal(context, textRenderer, stats, x, y);
        }
    }

    private void renderMinimal(DrawContext context, TextRenderer tr, NetworkStatistics stats,
                               int x, int y) {
        int ping = stats.getLastRttMs();
        double jitter = stats.getJitterMs();

        String trend = getTrendArrow(stats);
        String line = String.format("Ping: %dms %s | J:%.0fms", ping, trend, jitter);
        if (isMobile) {
            line += getNetworkTypeIndicator();
        }

        context.fill(x - 1, y - 1, x + tr.getWidth(line) + 1, y + tr.fontHeight + 1, 0x80000000);
        context.drawText(tr, line, x, y, pingColor(ping) | 0xFF000000, false);
    }

    private void renderDetailed(DrawContext context, TextRenderer tr, NetworkStatistics stats,
                                int x, int y) {
        int ping = stats.getLastRttMs();
        double jitter = stats.getJitterMs();
        double comprRatio = stats.getCompressionRatio() * 100;

        String[] lines = isMobile
                ? buildMobileDetailedLines(stats, ping, jitter, comprRatio)
                : buildDesktopDetailedLines(stats, ping, jitter, comprRatio);

        int width = 0;
        for (String line : lines) width = Math.max(width, tr.getWidth(line));

        context.fill(x - 2, y - 2,
                x + width + 2, y + lines.length * (tr.fontHeight + 1) + 2,
                0xA0000000);

        for (String line : lines) {
            context.drawText(tr, line, x, y, COLOR_WHITE | 0xFF000000, false);
            y += tr.fontHeight + 1;
        }
    }

    private String[] buildDesktopDetailedLines(NetworkStatistics stats, int ping, double jitter,
                                               double comprRatio) {
        return new String[]{
                "Cesium Network",
                String.format("Ping: %dms (min:%d avg:%.0f max:%d)",
                        ping, stats.getMinRttMs(), stats.getAvgRttMs(), stats.getMaxRttMs()),
                String.format("Jitter: %.1fms", jitter),
                String.format("Compress: %.0f%% saved", comprRatio),
                String.format("Flush: %d batched / %d direct",
                        stats.getFlushesBatched(), stats.getFlushesImmediate()),
        };
    }

    private String[] buildMobileDetailedLines(NetworkStatistics stats, int ping, double jitter,
                                              double comprRatio) {
        long ramUsedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long ramMaxMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return new String[]{
                "Cesium Mobile",
                String.format("Ping: %dms (avg:%.0f)", ping, stats.getAvgRttMs()),
                String.format("Jitter: %.1fms", jitter),
                String.format("RAM: %d/%dMB", ramUsedMb, ramMaxMb),
                String.format("Compress: %.0f%% saved", comprRatio),
                getNetworkTypeIndicator().trim(),
        };
    }

    private void renderWithGraph(DrawContext context, TextRenderer tr, NetworkStatistics stats,
                                 int x, int y, PingGraph graph) {
        renderDetailed(context, tr, stats, x, y);
        // Graph is rendered below the text block
        int graphY = y + 7 * (tr.fontHeight + 1) + 4;
        if (graph != null) {
            graph.update(stats);
            graph.render(context, x, graphY);
        }
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static NetworkStatistics getStats() {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            return engine != null ? engine.getNetworkStatistics() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static int pingColor(int pingMs) {
        if (pingMs < 80) return COLOR_GREEN;
        if (pingMs < 150) return COLOR_YELLOW;
        return COLOR_RED;
    }

    private String getTrendArrow(NetworkStatistics stats) {
        var history = stats.getRttHistory();
        if (history.size() < 5) return "-";
        int recent = history.get(history.size() - 1).rttMs();
        int older = history.get(history.size() - 5).rttMs();
        if (recent < older - 5) return "▼";
        if (recent > older + 5) return "▲";
        return "-";
    }

    private String getNetworkTypeIndicator() {
        // Reading actual Wi-Fi/4G status requires Android Context — unavailable here.
        // Return a placeholder; MobileNetworkTuner can update this in a future enhancement.
        return " [NET]";
    }

    public void toggleVisible() { visible = !visible; }
    public boolean isVisible() { return visible; }
}
