package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.xinantown.townresearch.display.LabPresenceDebouncer;
import com.xinantown.townresearch.display.ResearchBossBarPresentation;
import com.xinantown.townresearch.display.ResearchDisplayBusBridge;
import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Research progress BossBar:
 * <ul>
 *   <li>Prefer DisplayBus owned slot (source=townresearch, key=townName)</li>
 *   <li>Fallback to direct Bukkit BossBar when DisplayBus is missing</li>
 *   <li>Lab enter/leave debounced to reduce PlayerMoveEvent jitter</li>
 * </ul>
 */
public class ResearchBossBarManager implements Listener, ResearchDisplayBusBridge.LegacyBossBarSink {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final ResearchSettings settings;
    private final int maxLabs;
    private final LabPresenceDebouncer debouncer;
    private final ResearchDisplayBusBridge bridge;

    /** Legacy path only: townName -> shared BossBar */
    private final Map<String, BossBar> townBossBars = new HashMap<>();
    /** player -> town currently attached */
    private final Map<UUID, String> playerTown = new HashMap<>();

    public ResearchBossBarManager(TownResearchPlugin plugin, ResearchSettings settings) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
        this.debouncer = new LabPresenceDebouncer(ResearchBossBarPresentation.LEAVE_GRACE_TICKS);
        this.bridge = ResearchDisplayBusBridge.lookup(plugin.getLogger(), this);
        if (bridge.usesBus()) {
            plugin.getLogger().info("Research BossBar using DisplayBus.");
        }
    }

    /** Package-visible for tests. */
    ResearchBossBarManager(TownResearchPlugin plugin,
                           ResearchSettings settings,
                           LabPresenceDebouncer debouncer,
                           ResearchDisplayBusBridge bridge) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
        this.debouncer = debouncer;
        this.bridge = bridge;
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
        debouncer.clearAll();
        bridge.cleanup();
    }

    private void updateAll(Map<String, TownResearch> all, long nowMs, long nowTick) {
        Set<String> activeTowns = new HashSet<>();
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

            Set<Player> nearPlayers = findNearLabPlayers(townName, tr);
            Set<UUID> nearIds = new HashSet<>();
            for (Player p : nearPlayers) {
                UUID id = p.getUniqueId();
                nearIds.add(id);
                LabPresenceDebouncer.Decision decision = debouncer.onPresenceChanged(id, true, nowTick);
                if (decision == LabPresenceDebouncer.Decision.SHOW
                        || decision == LabPresenceDebouncer.Decision.KEEP) {
                    showPlayer(id, townName, snap, nowTick);
                }
            }

            for (UUID id : new HashSet<>(playerTown.keySet())) {
                if (nearIds.contains(id)) {
                    continue;
                }
                if (!townName.equals(playerTown.get(id))) {
                    continue;
                }
                LabPresenceDebouncer.Decision leave = debouncer.onPresenceChanged(id, false, nowTick);
                if (leave == LabPresenceDebouncer.Decision.HIDE
                        || debouncer.tick(id, nowTick) == LabPresenceDebouncer.Decision.HIDE) {
                    hidePlayer(id);
                } else if (debouncer.shouldDisplay(id, nowTick)) {
                    showPlayer(id, townName, snap, nowTick);
                }
            }
        }

        for (UUID id : new HashSet<>(playerTown.keySet())) {
            if (debouncer.tick(id, nowTick) == LabPresenceDebouncer.Decision.HIDE) {
                hidePlayer(id);
                continue;
            }
            String townName = playerTown.get(id);
            if (townName == null || !activeTowns.contains(townName)) {
                debouncer.clear(id);
                hidePlayer(id);
            }
        }

        if (!bridge.usesBus()) {
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
    }

    private void showPlayer(UUID playerId, String townName,
                            ResearchBossBarPresentation.Snapshot snap, long nowTick) {
        bridge.show(playerId, townName, snap.title(), snap.bossBarProgress(), nowTick);
        playerTown.put(playerId, townName);
    }

    private void hidePlayer(UUID playerId) {
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

    private Set<Player> findNearLabPlayers(String townName, TownResearch tr) {
        Set<Player> viewers = new HashSet<>();
        Town town = TownyAPI.getInstance().getTown(townName);
        if (town == null) {
            return viewers;
        }
        for (var resident : town.getResidents()) {
            Player p = Bukkit.getPlayer(resident.getUUID());
            if (p != null && p.isOnline() && isNearAnyLab(p, tr)) {
                viewers.add(p);
            }
        }
        return viewers;
    }

    private boolean isNearAnyLab(Player player, TownResearch tr) {
        for (ResearchLab lab : tr.getLabs()) {
            TownBlock tb = TownyAPI.getInstance().getTownBlock(player.getLocation());
            if (tb != null && tb.getWorld().getName().equals(lab.worldName())
                    && tb.getX() == lab.townBlockX()
                    && tb.getZ() == lab.townBlockZ()) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            return;
        }

        String townName = town.getName();
        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr == null || tr.getActiveProjects().isEmpty()) {
            return;
        }

        long nowTick = Bukkit.getCurrentTick();
        UUID id = player.getUniqueId();
        boolean nearLab = isNearAnyLab(player, tr);
        LabPresenceDebouncer.Decision decision = debouncer.onPresenceChanged(id, nearLab, nowTick);

        if (nearLab || debouncer.shouldDisplay(id, nowTick)) {
            ResearchBossBarPresentation.Snapshot snap = snapshotFor(tr, System.currentTimeMillis());
            if (snap != null
                    && (decision == LabPresenceDebouncer.Decision.SHOW
                    || decision == LabPresenceDebouncer.Decision.KEEP)) {
                showPlayer(id, townName, snap, nowTick);
            }
        }

        if (!nearLab) {
            if (decision == LabPresenceDebouncer.Decision.HIDE
                    || debouncer.tick(id, nowTick) == LabPresenceDebouncer.Decision.HIDE) {
                hidePlayer(id);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        debouncer.clear(id);
        hidePlayer(id);
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
