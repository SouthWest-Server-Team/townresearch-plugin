package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * Handles research completion checks, member grant/revoke on join/leave,
 * and displays research progress bars to players near labs.
 */
public class ResearchScheduler implements Listener {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final SlimefunBridge sfBridge;
    private final ResearchSettings settings;
    private final int maxLabs;
    private int taskId = -1;
    private int displayTaskId = -1;

    // Boss bars for research progress display
    private final Map<String, BossBar> townBossBars = new HashMap<>(); // townName -> boss bar

    public ResearchScheduler(TownResearchPlugin plugin, SlimefunBridge sfBridge, ResearchSettings settings) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.sfBridge = sfBridge;
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
    }

    public void start() {
        // Task 1: Check research completions every 30 seconds
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sfBridge.isAvailable()) return;
            checkCompletions();
        }, 600L, 600L).getTaskId();
        // Task 2: Update progress bar display every 2 seconds for smooth countdown
        displayTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!sfBridge.isAvailable()) return;
            long now = System.currentTimeMillis();
            Map<String, TownResearch> all = dataManager.loadAll(maxLabs);
            updateProgressBars(all, now);
        }, 40L, 40L).getTaskId();
        plugin.getLogger().info("Research scheduler started (completion:30s, display:2s).");
    }

    public void stop() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        if (displayTaskId != -1) { Bukkit.getScheduler().cancelTask(displayTaskId); displayTaskId = -1; }
        // Remove all boss bars
        for (BossBar bar : townBossBars.values()) {
            bar.removeAll();
        }
        townBossBars.clear();
    }

    /**
     * Check all towns for completed research projects and grant rewards.
     * Called by the 30-second timer.
     */
    private void checkCompletions() {
        long now = System.currentTimeMillis();
        Map<String, TownResearch> all = dataManager.loadAll(maxLabs);

        for (var entry : all.entrySet()) {
            TownResearch tr = entry.getValue();
            List<String> completed = tr.checkCompletions(now, settings);
            if (!completed.isEmpty()) {
                dataManager.save(entry.getKey(), tr);

                Town town = TownyAPI.getInstance().getTown(entry.getKey());
                if (town != null) {
                    for (var resident : town.getResidents()) {
                        Player player = Bukkit.getPlayer(resident.getUUID());
                        if (player != null && player.isOnline()) {
                            for (String key : completed) sfBridge.grantResearch(player, key);
                        }
                    }
                }
                for (String key : completed) {
                    plugin.getLogger().info("Town " + entry.getKey() + " completed research: " + key);
                }
            }
        }

        // Update boss bars for players near labs
        updateProgressBars(all, now);
    }

    /**
     * Update research progress boss bars for players standing near their town's labs.
     */
    private void updateProgressBars(Map<String, TownResearch> all, long nowMs) {
        // Remove old boss bars for towns that no longer have active research
        Set<String> activeTowns = new HashSet<>();
        Set<String> unusedBars = new HashSet<>(townBossBars.keySet());

        for (var entry : all.entrySet()) {
            String townName = entry.getKey();
            TownResearch tr = entry.getValue();

            if (tr.getActiveProjects().isEmpty()) {
                unusedBars.add(townName);
                continue;
            }

            activeTowns.add(townName);
            unusedBars.remove(townName);

            // Create or update boss bar for this town
            BossBar bar = townBossBars.computeIfAbsent(townName, k -> {
                BossBar b = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
                b.setVisible(true);
                return b;
            });

            // Build progress info from active projects
            StringBuilder title = new StringBuilder("§b§l⚡ 研究进度 ");
            List<ResearchProject> projects = new ArrayList<>(entry.getValue().getActiveProjects().values());

            if (projects.isEmpty()) {
                bar.removeAll();
                bar.setVisible(false);
                continue;
            }

            // Show first project info (or aggregate)
            ResearchProject first = projects.get(0);
            long effectiveMs = tr.getEffectiveDuration(first.sfKey(), first.durationMinutes(), settings) * 60000;
            long elapsed = nowMs - first.startedAt();
            double progress = Math.min(1.0, (double) elapsed / effectiveMs);
            int labCount = tr.countLabsResearching(first.sfKey());
            double labSpeed = settings.calcLabSpeedMultiplier(labCount);
            int paidLevel = tr.getPaidSpeedLevel();
            double paidSpeed = settings.getPaidSpeedLevelMultiplier(paidLevel);

            // Calculate remaining time
            long remainingMs = Math.max(0, effectiveMs - elapsed);
            long totalSec = remainingMs / 1000;
            String remainingStr;
            if (totalSec >= 3600) {
                remainingStr = String.format("%dh%02dm", totalSec / 3600, (totalSec % 3600) / 60);
            } else if (totalSec >= 60) {
                remainingStr = String.format("%dm%02ds", totalSec / 60, totalSec % 60);
            } else {
                remainingStr = totalSec + "s";
            }

            String displayKey = first.sfKey();
            if (displayKey.contains(":")) displayKey = displayKey.substring(displayKey.indexOf(':') + 1);

            title.append("§e").append(displayKey);
            title.append(" §7| §a").append(String.format("%.1f", progress * 100)).append("%");
            title.append(" §7| ⏱ ").append(remainingStr);
            title.append(" §7| 加速: §e").append(String.format("%.1f", labSpeed * paidSpeed)).append("x");
            if (projects.size() > 1) {
                title.append(" §7| §8+").append(projects.size() - 1).append(" 个");
            }

            bar.setTitle(title.toString());
            bar.setProgress(Math.max(0.01, progress));

            // Update players in the town who are near labs
            Set<Player> viewers = new HashSet<>();
            Town town = TownyAPI.getInstance().getTown(townName);
            if (town != null) {
                for (var resident : town.getResidents()) {
                    Player p = Bukkit.getPlayer(resident.getUUID());
                    if (p != null && p.isOnline() && isNearAnyLab(p, tr)) {
                        viewers.add(p);
                    }
                }
            }

            // Sync viewers
            for (Player p : bar.getPlayers()) {
                if (!viewers.contains(p)) bar.removePlayer(p);
            }
            for (Player p : viewers) {
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
        }

        // Remove boss bars for towns that no longer have active projects
        for (String tn : unusedBars) {
            BossBar bar = townBossBars.remove(tn);
            if (bar != null) {
                bar.removeAll();
                bar.setVisible(false);
            }
        }
    }

    /**
     * Check if a player is standing near one of the town's research labs.
     */
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

    // === Player events ===

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Process pending revokes first
        Map<String, TownResearch> all = dataManager.loadAll(maxLabs);
        UUID uuid = event.getPlayer().getUniqueId();
        for (var entry : all.entrySet()) {
            if (entry.getValue().getPendingRevokes().contains(uuid)) {
                for (String key : entry.getValue().getCompleted()) {
                    sfBridge.revokeResearch(event.getPlayer(), key);
                }
                entry.getValue().removePendingRevoke(uuid);
                dataManager.save(entry.getKey(), entry.getValue());
            }
        }
        grantTownResearch(event.getPlayer());
    }

    @EventHandler
    public void onTownRemoveResident(TownRemoveResidentEvent event) {
        Player player = Bukkit.getPlayer(event.getResident().getUUID());
        UUID uuid = event.getResident().getUUID();
        String townName = event.getTown().getName();

        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr == null) return;

        if (player != null && player.isOnline() && sfBridge.isAvailable()) {
            // Online: revoke immediately
            for (String key : tr.getCompleted()) {
                sfBridge.revokeResearch(player, key);
            }
        } else {
            // Offline: mark for revoke on next login
            tr.addPendingRevoke(uuid);
            dataManager.save(townName, tr);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process block changes to reduce lag
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player player = event.getPlayer();
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) return;

        String townName = town.getName();
        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr == null || tr.getActiveProjects().isEmpty()) return;

        boolean nearLab = isNearAnyLab(player, tr);
        BossBar bar = townBossBars.get(townName);

        if (nearLab) {
            // Create bar on-demand if not yet created (don't wait for 30s tick)
            if (bar == null) {
                bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
                bar.setVisible(true);
                townBossBars.put(townName, bar);
            }
            // Refresh bar content immediately
            updateSingleTownBar(townName, tr, System.currentTimeMillis(), bar);
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        } else if (bar != null && bar.getPlayers().contains(player)) {
            bar.removePlayer(player);
        }
    }

    /**
     * Refresh a single town's boss bar with current research progress.
     */
    private void updateSingleTownBar(String townName, TownResearch tr, long nowMs, BossBar bar) {
        List<ResearchProject> projects = new ArrayList<>(tr.getActiveProjects().values());
        if (projects.isEmpty()) {
            bar.removeAll();
            bar.setVisible(false);
            townBossBars.remove(townName);
            return;
        }
        ResearchProject first = projects.get(0);
        long effectiveMs = tr.getEffectiveDuration(first.sfKey(), first.durationMinutes(), settings) * 60000;
        long elapsed = nowMs - first.startedAt();
        double progress = Math.min(1.0, (double) elapsed / effectiveMs);
        int labCount = tr.countLabsResearching(first.sfKey());
        double labSpeed = settings.calcLabSpeedMultiplier(labCount);
        double paidSpeed = settings.getPaidSpeedLevelMultiplier(tr.getPaidSpeedLevel());

        // Calculate remaining time
        long remainingMs = Math.max(0, effectiveMs - elapsed);
        long totalSec = remainingMs / 1000;
        String remainingStr;
        if (totalSec >= 3600) {
            remainingStr = String.format("%dh%02dm", totalSec / 3600, (totalSec % 3600) / 60);
        } else if (totalSec >= 60) {
            remainingStr = String.format("%dm%02ds", totalSec / 60, totalSec % 60);
        } else {
            remainingStr = totalSec + "s";
        }

        String displayKey = first.sfKey();
        if (displayKey.contains(":")) displayKey = displayKey.substring(displayKey.indexOf(':') + 1);

        StringBuilder title = new StringBuilder("§b§l⚡ 研究进度 ");
        title.append("§e").append(displayKey);
        title.append(" §7| §a").append(String.format("%.1f", progress * 100)).append("%");
        title.append(" §7| ⏱ ").append(remainingStr);
        title.append(" §7| 加速: §e").append(String.format("%.1f", labSpeed * paidSpeed)).append("x");
        if (projects.size() > 1) {
            title.append(" §7| §8+").append(projects.size() - 1).append(" 个更多项目");
        }

        bar.setTitle(title.toString());
        bar.setProgress(Math.max(0.01, progress));
    }

    private void grantTownResearch(Player player) {
        if (!sfBridge.isAvailable()) return;

        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) return;

        TownResearch tr = dataManager.load(town.getName(), maxLabs);
        if (tr == null) return;

        for (String key : tr.getCompleted()) {
            sfBridge.grantResearch(player, key);
        }
    }
}
