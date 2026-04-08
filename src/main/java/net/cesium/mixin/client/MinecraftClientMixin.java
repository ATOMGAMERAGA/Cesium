package net.cesium.mixin.client;

import net.cesium.CesiumMod;
import net.cesium.hud.CesiumHud;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into the Minecraft client lifecycle to:
 * <ul>
 *   <li>Shut down Cesium cleanly when the game exits.</li>
 *   <li>Register HUD rendering after the client is initialized.</li>
 * </ul>
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Inject(method = "stop", at = @At("HEAD"))
    private void cesium$onStop(CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine != null) {
                engine.shutdown();
            }
        } catch (Exception e) {
            CesiumMod.LOGGER.warn("[Cesium] Error during shutdown: {}", e.getMessage());
        }
    }
}
