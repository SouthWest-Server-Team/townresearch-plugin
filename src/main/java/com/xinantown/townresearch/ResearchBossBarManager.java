package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.xinantown.townresearch.display.ResearchBossBarPresentation;
import com.xinantown.townresearch.display.ResearchBossBarVisibility;
import com.xinantown.townresearch.display.ResearchDisplayBusBridge;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Research progress BossBar:
 * <ul>
 *   <li>Visibility is plot-only: standing on a lab TownBlock is enough</li>
 *   <li>No PlayerMoveEvent — periodic refresh scans current plots</li>
 *   <li>Prefer DisplayBus owned slot; dual-write Bukkit BossBar for client visibility</li>
 * </ul>
 */
public class ResearchBossBarManager implements Listener, ResearchDisplayBusBridge.LegacyBossBarSink {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final ResearchSettings settings;
    private final int maxLabs;
    private final ResearchDisplayBusBridge bridge;
    private final boolean debugLogging;

    /** Legacy path: townName -> shared BossBar */
    private final Map<String, BossBar> townBossBars = new HashMap<>();
    /** player -> town currently attached */
    private final Map<UUID, String> playerTown = new HashMap<>();

    public ResearchBossBarManager(TownResearchPlugin plugin, ResearchSettings settings) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
        this.bridge = ResearchDisplayBusBridge.lookup(plugin.getLogger(), this);
        this.debugLogging = ResearchBossBarVisibility.debugLoggingEnabled(
                plugin.getConfig().getBoolean("bossbar-debug", false));
        if (bridge.usesBus()) {
            plugin.getLogger().info("Research BossBar using DisplayBus.");
        }
        if (debugLogging) {
            plugin.getLogger().info("Research BossBar debug logging enabled (plot-only, no move).");
        }
    }

    /** Package-visible for tests. */
    ResearchBossBarManager(TownResearchPlugin plugin,
                           ResearchSettings settings,
                           ResearchDisplayBusBridge bridge) {
        this(plugin, settings, bridge, false);
    }

    /** Package-visible for tests. */
    ResearchBossBarManager(TownResearchPlugin plugin,
                           ResearchSettings settings,
                           ResearchDisplayBusBridge bridge,
                           boolean debugLogging) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
        this.bridge = bridge;
        this.debugLogging = ResearchBossBarVisibility.debugLoggingEnabled(debugLogging);
    }

    public void refreshBars() {
        long nowMs = System.currentTimeMillis();
        long nowTick = Bukkit.getCurrentTick();
        Map<String, TownResearch> all = dataManager.loadAll(maxLabs);
        updateAll(all, nowMs, nowTick);
    }

    public void cleanup() {
        for (UUID playerId : new HashSet<>(playerTown.keySet())) {
            hidePlayer(playerId);
        }
        for (BossBar bar : townBossBars.values()) {
            bar.removeAll();
        }
        townBossBars.clear();
        playerTown.clear();
        bridge.cleanup();
    }

    /**
     * Plot-only refresh: show while standing on a lab TownBlock with active research;
     * hide immediately when leaving that plot. No movement listener required.
     */
    private void updateAll(Map<String, TownResearch> all, long nowMs, long nowTick) {
        Set<String> activeTowns = new HashSet<>();
        Set<UUID> nearIds = new HashSet<>();

        for (var entry : all.entrySet()) {
            String townName = entry.getKey();
            TownResearch tr = entry.getValue();
            if (tr.getActiveProjects().isEmpty()) {
                continue;
            }
            activeTowns.add(townName);
            ResearchBossBarPresentation.Snapshot snap = snapshotFor(tr, nowMs);
            if (snap == null) {
                continue;
            }

            for (Player p : findNearLabPlayers(townName, tr)) {
                UUID id = p.getUniqueId();
                nearIds.add(id);
                showPlayer(id, townName, snap, nowTick);
            }
        }

        // Plot-only: leave lab chunk → hide immediately (no move listener / grace window).
        for (UUID id : new HashSet<>(playerTown.keySet())) {
            if (!nearIds.contains(id)) {
                hidePlayer(id);
            }
        }

        Set<String> unused = new HashSet<>(townBossBars.keySet());
        unused.removeAll(activeTowns);
        for (String tn : unused) {
            BossBar bar = townBossBars.remove(tn);
            if (bar != null) {
                bar.removeAll();
                bar.setVisible(false);
            }
        }
    }

    private void showPlayer(UUID playerId, String townName,
                            ResearchBossBarPresentation.Snapshot snap, long nowTick) {
        debug("show player=" + playerId + " town=" + townName + " progress=" + snap.bossBarProgress());
        bridge.show(playerId, townName, snap.title(), snap.bossBarProgress(), nowTick);
        playerTown.put(playerId, townName);
    }

    private void hidePlayer(UUID playerId) {
        debug("hide player=" + playerId);
        bridge.hide(playerId);
        playerTown.remove(playerId);
    }

    private ResearchBossBarPresentation.Snapshot snapshotFor(TownResearch tr, long nowMs) {
        List<ResearchProject> projects = new ArrayList<>(tr.getActiveProjects().values());
        if (projects.isEmpty()) {
            return null;
        }
        ResearchProject first = projects.get(0);
        long effectiveMinutes = tr.getEffectiveDuration(first.sfKey(), first.durationMinutes(), settings);
        int labCount = tr.countLabsResearching(first.sfKey());
        double speed = settings.calcLabSpeedMultiplier(labCount)
                * settings.getPaidSpeedLevelMultiplier(tr.getPaidSpeedLevel());
        return ResearchBossBarPresentation.build(
                first.sfKey(),
                first.startedAt(),
                effectiveMinutes,
                labCount,
                speed,
                nowMs,
                Math.max(0, projects.size() - 1)
        );
    }

    /**
     * Collect every online player standing on any lab plot of this town's research.
     * Residency is not required — visitors / townless players are included.
     */
    private Set<Player> findNearLabPlayers(String townName, TownResearch tr) {
        Set<Player> viewers = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) {
                continue;
            }
            boolean near = isNearAnyLab(p, tr);
            debug("nearLab scan town=" + townName + " player=" + p.getName() + " near=" + near);
            if (ResearchBossBarVisibility.includeOnlineViewer(true, near)) {
                viewers.add(p);
            }
        }
        return viewers;
    }

    private boolean isNearAnyLab(Player player, TownResearch tr) {
        TownBlock tb = TownyAPI.getInstance().getTownBlock(player.getLocation());
        if (tb == null) {
            return false;
        }
        return ResearchBossBarVisibility.matchesAnyLab(
                tb.getWorld().getName(),
                tb.getX(),
                tb.getZ(),
                tr.getLabs()
        );
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hidePlayer(event.getPlayer().getUniqueId());
    }

    private void debug(String message) {
        if (!debugLogging) {
            return;
        }
        Logger logger = plugin != null ? plugin.getLogger() : null;
        if (logger != null) {
            logger.info("[BossBarDebug] " + message);
        }
    }

    // ==================== LegacyBossBarSink ====================

    @Override
    public void show(UUID playerId, String townName, String title, double progress) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        BossBar bar = townBossBars.computeIfAbsent(townName, k -> {
            BossBar b = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
            b.setVisible(true);
            return b;
        });
        bar.setTitle(title);
        bar.setProgress(Math.max(0.01, Math.min(1.0, progress)));

        String previous = playerTown.put(playerId, townName);
        if (previous != null && !previous.equals(townName)) {
            BossBar old = townBossBars.get(previous);
            if (old != null) {
                old.removePlayer(player);
            }
        }
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    @Override
    public void hide(UUID playerId) {
        String townName = playerTown.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (townName != null) {
            BossBar bar = townBossBars.get(townName);
            if (bar != null && player != null) {
                bar.removePlayer(player);
            }
            return;
        }
        if (player != null) {
            for (BossBar bar : townBossBars.values()) {
                bar.removePlayer(player);
            }
        }
    }

    @Override
    public void cleanupLegacy() {
        // manager.cleanup already clears bars
    }
}
