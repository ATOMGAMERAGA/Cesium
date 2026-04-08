package net.cesium.mixin.network;

import net.minecraft.network.NetworkState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder for future NetworkState-level optimizations (packet ID caching, etc.).
 */
@Mixin(NetworkState.class)
public abstract class NetworkStateMixin {
    // Reserved for future optimizations.
}
