package net.cesium.mixin.client;

import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.option.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for {@link MultiplayerScreen}'s server list field.
 */
@Mixin(MultiplayerScreen.class)
public interface MultiplayerScreenAccessor {

    @Accessor("serverList")
    ServerList cesium$getServerList();
}
