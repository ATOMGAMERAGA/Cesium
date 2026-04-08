package net.cesium.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.cesium.CesiumConfig;
import net.cesium.CesiumMod;
import net.cesium.core.OptimizationProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Registers Cesium's configuration screen with Mod Menu.
 *
 * <p>The config screen lets players toggle key optimisations and switch
 * between the built-in optimisation profiles without editing JSON files.
 * All changes are saved immediately via {@link CesiumConfig#save()}.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CesiumConfigScreen::new;
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static final class CesiumConfigScreen extends Screen {

        private static final int BTN_W = 150;
        private static final int BTN_H = 20;
        private static final int MARGIN = 8;
        private static final int ROW = BTN_H + 4;

        private final Screen parent;
        private final CesiumConfig config;

        public CesiumConfigScreen(Screen parent) {
            super(Text.translatable("cesium.config.title"));
            this.parent = parent;
            this.config = CesiumMod.getInstance().getConfig();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 36;

            // ── Profile row ──────────────────────────────────────────────────
            // Four profile buttons side-by-side
            int profileBtnW = (BTN_W * 2 + MARGIN) / 2 - 1;
            addProfileButton(cx - profileBtnW * 2 - MARGIN + 2, y, profileBtnW, OptimizationProfile.CONSERVATIVE);
            addProfileButton(cx - profileBtnW    - MARGIN / 2,  y, profileBtnW, OptimizationProfile.BALANCED);
            addProfileButton(cx                  + MARGIN / 2,  y, profileBtnW, OptimizationProfile.AGGRESSIVE);
            addProfileButton(cx + profileBtnW    + MARGIN - 2,  y, profileBtnW, OptimizationProfile.MOBILE);
            y += ROW + 6;

            // ── Toggle options ───────────────────────────────────────────────
            int left  = cx - BTN_W - MARGIN / 2;
            int right = cx + MARGIN / 2;

            addToggle(left,  y, "cesium.config.flush_consolidation",
                    config.isFlushConsolidationEnabled(),
                    pressed -> setFlushConsolidation(pressed));
            addToggle(right, y, "cesium.config.compression",
                    config.isCompressionEnabled(),
                    pressed -> setCompression(pressed));
            y += ROW;

            addToggle(left,  y, "cesium.config.hud.enabled",
                    config.isHudEnabled(),
                    pressed -> setHudEnabled(pressed));
            addToggle(right, y, "cesium.config.native_transport",
                    config.isNativeTransportEnabled(),
                    pressed -> setNativeTransport(pressed));
            y += ROW;

            addToggle(left,  y, "cesium.config.spline_interpolation",
                    config.isSplineInterpolation(),
                    pressed -> setSplineInterpolation(pressed));
            addToggle(right, y, "cesium.config.happy_eyeballs",
                    config.isHappyEyeballs(),
                    pressed -> setHappyEyeballs(pressed));
            y += ROW;

            addToggle(left,  y, "cesium.config.jitter_buffer",
                    config.isJitterBufferEnabled(),
                    pressed -> setJitterBuffer(pressed));
            y += ROW + 6;

            // ── HUD mode cycle ────────────────────────────────────────────────
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("HUD: " + config.getHudMode()),
                    btn -> {
                        String next = switch (config.getHudMode()) {
                            case "MINIMAL" -> "DETAILED";
                            case "DETAILED" -> "GRAPH";
                            default -> "MINIMAL";
                        };
                        setHudMode(next);
                        btn.setMessage(Text.literal("HUD: " + next));
                    })
                    .dimensions(left, y, BTN_W, BTN_H)
                    .build());
            y += ROW + 6;

            // ── Done button ───────────────────────────────────────────────────
            addDrawableChild(ButtonWidget.builder(
                    Text.translatable("gui.done"),
                    btn -> close())
                    .dimensions(cx - 75, y, 150, BTN_H)
                    .build());
        }

        // ── Widget helpers ────────────────────────────────────────────────────

        private void addProfileButton(int x, int y, int w, OptimizationProfile profile) {
            boolean active = config.getProfile() == profile;
            String label = Text.translatable("cesium.profile." + profile.name().toLowerCase())
                    .getString();
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(active ? "§a[" + label + "]" : label),
                    btn -> switchProfile(profile))
                    .dimensions(x, y, w, BTN_H)
                    .build());
        }

        private void addToggle(int x, int y, String key, boolean current,
                               java.util.function.Consumer<Boolean> setter) {
            String label = Text.translatable(key).getString();
            addDrawableChild(ButtonWidget.builder(
                    Text.literal(label + ": " + (current ? "§aON" : "§cOFF")),
                    btn -> {
                        boolean newVal = !btn.getMessage().getString().contains("ON");
                        setter.accept(newVal);
                        btn.setMessage(Text.literal(label + ": " + (newVal ? "§aON" : "§cOFF")));
                    })
                    .dimensions(x, y, BTN_W, BTN_H)
                    .build());
        }

        // ── Config mutators (save after every change) ─────────────────────────

        private void switchProfile(OptimizationProfile profile) {
            // Reflect the profile change then rebuild the button list.
            setProfile(profile);
            clearChildren();
            init();
        }

        private void setProfile(OptimizationProfile p)        { reflectField("profile", p); }
        private void setFlushConsolidation(boolean v)          { reflectField("flushConsolidationEnabled", v); }
        private void setCompression(boolean v)                 { reflectField("compressionEnabled", v); }
        private void setHudEnabled(boolean v)                  { reflectField("hudEnabled", v); }
        private void setNativeTransport(boolean v)             { reflectField("nativeTransportEnabled", v); }
        private void setSplineInterpolation(boolean v)         { reflectField("splineInterpolation", v); }
        private void setHappyEyeballs(boolean v)               { reflectField("happyEyeballs", v); }
        private void setJitterBuffer(boolean v)                { reflectField("jitterBufferEnabled", v); }
        private void setHudMode(String mode)                   { reflectField("hudMode", mode); }

        /**
         * Uses reflection to update a private field on {@link CesiumConfig}
         * and immediately persists the change to disk.
         */
        private void reflectField(String fieldName, Object value) {
            try {
                var field = CesiumConfig.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(config, value);
                config.save();
            } catch (Exception e) {
                CesiumMod.LOGGER.warn("[Cesium] Config update failed for {}: {}", fieldName, e.getMessage());
            }
        }

        // ── Rendering ─────────────────────────────────────────────────────────

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            renderBackground(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

            // Section labels
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("cesium.config.profile"), width / 2 - 155, 24, 0xAAAAAA);

            super.render(ctx, mouseX, mouseY, delta);

            // Version footer
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Cesium v" + CesiumMod.getVersion()
                            + "  |  Profile: " + config.getProfile().name()),
                    width / 2, height - 10, 0x555555);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
}
