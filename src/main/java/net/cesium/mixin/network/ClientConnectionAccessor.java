package net.cesium.mixin.network;

import io.netty.channel.Channel;
import net.minecraft.network.ClientConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to expose {@link ClientConnection}'s private {@code channel} field.
 */
@Mixin(ClientConnection.class)
public interface ClientConnectionAccessor {

    @Accessor("channel")
    Channel cesium$getChannel();
}
