package com.xinantown.townresearch.display;

import com.xinantown.display.api.DisplayBusService;
import com.xinantown.display.core.BossBarRequest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft DisplayBus adapter for research BossBar.
 * When bus is absent, falls back to {@link LegacyBossBarSink}.
 */
public final class ResearchDisplayBusBridge {

    public interface LegacyBossBarSink {
        void show(UUID playerId, String townName, String title, double progress);

        void hide(UUID playerId);

        void cleanupLegacy();
    }

    private final DisplayBusService bus;
    private final LegacyBossBarSink legacy;

    public ResearchDisplayBusBridge(DisplayBusService bus) {
        this(bus, null);
    }

    public ResearchDisplayBusBridge(DisplayBusService bus, LegacyBossBarSink legacy) {
        this.bus = bus;
        this.legacy = legacy;
    }

    public static ResearchDisplayBusBridge lookup(Logger logger, LegacyBossBarSink legacy) {
        try {
            RegisteredServiceProvider<DisplayBusService> rsp =
                    Bukkit.getServicesManager().getRegistration(DisplayBusService.class);
            if (rsp == null || rsp.getProvider() == null) {
                if (logger != null) {
                    logger.warning("DisplayBus missing; fallback to direct BossBar write.");
                }
                return new ResearchDisplayBusBridge(null, legacy);
            }
            return new ResearchDisplayBusBridge(rsp.getProvider(), legacy);
        } catch (NoClassDefFoundError | Exception ex) {
            if (logger != null) {
                logger.warning("DisplayBus missing; fallback to direct BossBar write.");
            }
            return new ResearchDisplayBusBridge(null, legacy);
        }
    }

    public boolean usesBus() {
        return bus != null;
    }

    public void show(UUID playerId, String townName, String title, double progress, long nowTick) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(townName, "townName");
        Objects.requireNonNull(title, "title");
        if (bus != null) {
            bus.requestBossBar(
                    playerId,
                    new BossBarRequest(
                            ResearchBossBarPresentation.SOURCE,
                            ResearchBossBarPresentation.keyForTown(townName),
                            title,
                            progress,
                            ResearchBossBarPresentation.TTL_TICKS),
                    nowTick);
            return;
        }
        if (legacy != null) {
            legacy.show(playerId, townName, title, progress);
        }
    }

    public void hide(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (bus != null) {
            bus.clearSource(playerId, ResearchBossBarPresentation.SOURCE);
            return;
        }
        if (legacy != null) {
            legacy.hide(playerId);
        }
    }

    public void cleanup() {
        if (legacy != null) {
            legacy.cleanupLegacy();
        }
    }
}
