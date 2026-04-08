package net.cesium;

import net.cesium.command.CesiumCommand;
import net.cesium.core.CesiumNetworkEngine;
import net.cesium.core.OptimizationProfile;
import net.cesium.core.PlatformDetector;
import net.cesium.hud.CesiumHud;
import net.cesium.mobile.MobileBatteryAware;
import net.cesium.mobile.MobileDetector;
import net.cesium.mobile.MobileMemoryGuard;
import net.cesium.netty.allocator.DirectMemoryPool;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class CesiumMod implements ClientModInitializer {

    public static final String MOD_ID = "cesium";
    public static final String MOD_NAME = "Cesium";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static CesiumMod instance;
    private CesiumConfig config;
    private CesiumNetworkEngine networkEngine;
    private MobileMemoryGuard mobileMemoryGuard;
    private MobileBatteryAware mobileBatteryAware;

    @Override
    public void onInitializeClient() {
        instance = this;

        LOGGER.info("[Cesium] Initializing {} v{}", MOD_NAME, getVersion());

        // Must run before any Netty channel opens — configures system properties.
        DirectMemoryPool.configure();

        // Detect platform (OS / arch / Android).
        PlatformDetector.detect();
        LOGGER.info("[Cesium] Platform: {}", PlatformDetector.getPlatformDescription());

        // Load configuration (auto-switches to MOBILE profile on Android).
        config = CesiumConfig.load();
        LOGGER.info("[Cesium] Profile: {}", config.getProfile());

        // Mobile-specific sub-systems.
        if (MobileDetector.isAndroid()) {
            mobileMemoryGuard = new MobileMemoryGuard(config);
            mobileBatteryAware = new MobileBatteryAware(config);
        }

        // Initialize the main network engine.
        networkEngine = new CesiumNetworkEngine(config);
        networkEngine.initialize();

        // Register HUD overlay.
        CesiumHud hud = new CesiumHud();
        CesiumHud.register(hud);

        // Register /cesium command.
        CesiumCommand.setHudInstance(hud);
        CesiumCommand.register();

        LOGGER.info("[Cesium] Initialization complete.");
    }

    public static CesiumMod getInstance() {
        return instance;
    }

    public CesiumConfig getConfig() {
        return config;
    }

    public CesiumNetworkEngine getNetworkEngine() {
        return networkEngine;
    }

    public MobileMemoryGuard getMobileMemoryGuard() {
        return mobileMemoryGuard;
    }

    public MobileBatteryAware getMobileBatteryAware() {
        return mobileBatteryAware;
    }

    public static OptimizationProfile getActiveProfile() {
        if (instance == null || instance.config == null) {
            return OptimizationProfile.BALANCED;
        }
        return instance.config.getProfile();
    }

    public static String getVersion() {
        String v = CesiumMod.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
