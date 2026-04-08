package net.cesium.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.cesium.CesiumMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Registers Cesium's configuration screen with Mod Menu.
 *
 * <p>This class is referenced in {@code fabric.mod.json} under the
 * {@code modmenu} entrypoint and is only loaded when Mod Menu is present.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CesiumConfigScreen::new;
    }

    /**
     * Minimal config screen placeholder.
     *
     * <p>Full YACL/Cloth Config integration is planned for Phase 8.
     */
    public static class CesiumConfigScreen extends Screen {

        private final Screen parent;

        public CesiumConfigScreen(Screen parent) {
            super(Text.literal("Cesium Settings"));
            this.parent = parent;
        }

        @Override
        public void close() {
            if (client != null) client.setScreen(parent);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(textRenderer,
                    "Cesium v" + CesiumMod.getVersion(), width / 2, height / 2 - 20, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    "Profile: " + CesiumMod.getActiveProfile(),
                    width / 2, height / 2, 0xAAAAAA);
            context.drawCenteredTextWithShadow(textRenderer,
                    "Full config UI coming in Phase 8",
                    width / 2, height / 2 + 20, 0x777777);
        }
    }
}
