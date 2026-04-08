package net.cesium.mixin.network;

import net.cesium.CesiumMod;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;

/**
 * Speeds up the server list ping by pre-warming the DNS cache before the
 * connection attempt begins.
 *
 * <p>Targets the package-private {@code ping(InetSocketAddress, ServerAddress, ServerInfo)}
 * method which is called for each server entry in the list.
 */
@Mixin(MultiplayerServerListPinger.class)
public abstract class ServerPingerMixin {

    @Inject(method = "ping", at = @At("HEAD"))
    private void cesium$onPing(InetSocketAddress address, ServerAddress serverAddress,
                               ServerInfo serverInfo, CallbackInfo ci) {
        try {
            var engine = CesiumMod.getInstance().getNetworkEngine();
            if (engine == null) return;

            var resolver = engine.getDnsResolver();
            if (resolver != null && serverInfo != null && serverInfo.address != null) {
                resolver.resolveAsync(serverInfo.address);
            }
        } catch (Exception e) {
            CesiumMod.LOGGER.debug("[Cesium] ServerPingerMixin: {}", e.getMessage());
        }
    }
}
