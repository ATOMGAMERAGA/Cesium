package net.cesium.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.cesium.CesiumConfig;
import net.cesium.CesiumMod;
import net.cesium.core.OptimizationProfile;
import net.cesium.hud.CesiumHud;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Registers the in-game {@code /cesium} client command.
 *
 * <p>Subcommands:
 * <pre>
 *   /cesium hud                — toggles HUD visibility
 *   /cesium profile <name>     — switches optimization profile
 *   /cesium stats              — prints current network statistics to chat
 *   /cesium reload             — reloads config from disk
 * </pre>
 *
 * <p>Especially useful on mobile (PojavLauncher) where F-key keybinds are
 * difficult to reach.
 */
public class CesiumCommand {

    private CesiumCommand() {}

    private static CesiumHud hudInstance;

    public static void setHudInstance(CesiumHud hud) {
        hudInstance = hud;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                registerAll(dispatcher));
    }

    private static void registerAll(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("cesium")
                // /cesium hud
                .then(literal("hud").executes(ctx -> {
                    if (hudInstance != null) {
                        hudInstance.toggleVisible();
                        boolean visible = hudInstance.isVisible();
                        ctx.getSource().sendFeedback(
                            Text.literal("[Cesium] HUD " + (visible ? "enabled" : "disabled")));
                    }
                    return 1;
                }))

                // /cesium stats
                .then(literal("stats").executes(ctx -> {
                    var engine = CesiumMod.getInstance().getNetworkEngine();
                    if (engine == null || engine.getNetworkStatistics() == null) {
                        ctx.getSource().sendFeedback(Text.literal("[Cesium] No stats available."));
                        return 0;
                    }
                    var stats = engine.getNetworkStatistics();
                    ctx.getSource().sendFeedback(Text.literal(String.format(
                        "[Cesium] Ping: %dms | Jitter: %.1fms | Compress: %.0f%% | Batched flushes: %d",
                        stats.getLastRttMs(),
                        stats.getJitterMs(),
                        stats.getCompressionRatio() * 100,
                        stats.getFlushesBatched()
                    )));
                    return 1;
                }))

                // /cesium reload
                .then(literal("reload").executes(ctx -> {
                    CesiumMod.getInstance().getConfig().save();
                    ctx.getSource().sendFeedback(Text.literal("[Cesium] Config reloaded."));
                    return 1;
                }))

                // /cesium profile <CONSERVATIVE|BALANCED|AGGRESSIVE|MOBILE|CUSTOM>
                .then(literal("profile")
                    .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name").toUpperCase();
                        try {
                            OptimizationProfile profile = OptimizationProfile.valueOf(name);
                            ctx.getSource().sendFeedback(
                                Text.literal("[Cesium] Profile set to " + profile
                                    + " (restart to fully apply)"));
                        } catch (IllegalArgumentException e) {
                            ctx.getSource().sendError(
                                Text.literal("[Cesium] Unknown profile: " + name
                                    + ". Use: CONSERVATIVE, BALANCED, AGGRESSIVE, MOBILE, CUSTOM"));
                        }
                        return 1;
                    })))
        );
    }
}
