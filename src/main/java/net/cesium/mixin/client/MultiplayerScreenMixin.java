package net.cesium.mixin.client;

import net.cesium.CesiumMod;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.option.ServerList;

import java.util.ArrayList;
import java.util.List;

/**
 * Warms up the DNS cache when the multiplayer server list is opened so that
 * subsequent connections start immediately without a DNS lookup delay.
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin {

    @Shadow
    private ServerList serverList;

    @Inject(method = "init", at = @At("TAIL"))
    private void cesium$onInit(CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null || engine.getDnsResolver() == null) return;
            if (serverList == null) return;

            List<String> hostnames = new ArrayList<>();
            for (int i = 0; i < serverList.size(); i++) {
                var info = serverList.get(i);
                if (info != null && info.address != null && !info.address.isBlank()) {
                    hostnames.add(info.address);
                }
            }
            if (!hostnames.isEmpty()) {
                engine.getDnsResolver().warmUp(hostnames);
                CesiumMod.LOGGER.debug("[Cesium] DNS warm-up for {} servers", hostnames.size());
            }
        } catch (Exception e) {
            CesiumMod.LOGGER.debug("[Cesium] MultiplayerScreenMixin: {}", e.getMessage());
        }
    }
}
