package net.cesium.mobile;

import net.cesium.CesiumConfig;

/**
 * Provides HUD scaling parameters tuned for mobile touchscreen displays.
 *
 * <p>On small screens with high DPI, Minecraft's default font and layout sizes
 * become illegible. This class computes appropriate scale factors so that the
 * Cesium HUD remains readable without occluding too much of the screen.
 */
public class MobileHudScaler {

    private final float baseScale;

    public MobileHudScaler(CesiumConfig config) {
        this.baseScale = config.getTouchHudScale();
    }

    /**
     * Returns the HUD text scale multiplier.
     *
     * <p>A value of 1.0 means no scaling (desktop default).
     * Mobile default is 1.5×, configurable via {@code mobile.touch_hud_scale}.
     */
    public float getTextScale() {
        return baseScale;
    }

    /**
     * Returns the recommended HUD line height in pixels for the given scale.
     *
     * @param fontHeight Minecraft's base font height (usually 9px)
     */
    public int getLineHeight(int fontHeight) {
        return Math.round(fontHeight * baseScale) + 2;
    }

    /**
     * Returns the recommended left-edge padding in pixels.
     */
    public int getPaddingX() {
        return Math.round(6 * baseScale);
    }

    /**
     * Returns the recommended top-edge padding in pixels.
     */
    public int getPaddingY() {
        return Math.round(6 * baseScale);
    }

    /**
     * Returns the background fill alpha (0–255).
     * Slightly more opaque on mobile for readability in bright outdoor conditions.
     */
    public int getBackgroundAlpha() {
        return 0xB0; // ~69% opacity
    }
}
