package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
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
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.*;

/**
 * Manages research progress boss bars:
 * <ul>
 *   <li>2s refresh — updates progress bars for all towns with active research</li>
 *   <li>PlayerMoveEvent — adds/removes players from bars based on lab proximity</li>
 * </ul>
 */
public class ResearchBossBarManager implements Listener {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final ResearchSettings settings;
    private final int maxLabs;
    private final Map<String, BossBar> townBossBars = new HashMap<>(); // townName -> boss bar

    public ResearchBossBarManager(TownResearchPlugin plugin, ResearchSettings settings) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
    }

    /**
     * Refresh all boss bars. Called by the 2-second timer.
     */
    public void refreshBars() {
        long now = System.currentTimeMillis();
        Map<String, TownResearch> all = dataManager.loadAll(maxLabs);
        updateAllBars(all, now);
    }

    /**
     * Remove all boss bars on shutdown.
     */
    public void cleanup() {
        for (BossBar bar : townBossBars.values()) {
            bar.removeAll();
        }
        townBossBars.clear();
    }

    // ==================== Bar updates ====================

    private void updateAllBars(Map<String, TownResearch> all, long nowMs) {
        Set<String> unusedBars = new HashSet<>(townBossBars.keySet());

        for (var entry : all.entrySet()) {
            String townName = entry.getKey();
            TownResearch tr = entry.getValue();

            if (tr.getActiveProjects().isEmpty()) {
                unusedBars.add(townName);
                continue;
            }

            unusedBars.remove(townName);

            List<ResearchProject> projects = new ArrayList<>(entry.getValue().getActiveProjects().values());
            if (projects.isEmpty()) continue;

            BossBar bar = townBossBars.computeIfAbsent(townName, k -> {
                BossBar b = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
                b.setVisible(true);
                return b;
            });

            String title = buildBarTitle(tr, projects.get(0), nowMs,
                    projects.size() > 1 ? " §7| §8+" + (projects.size() - 1) + " 个" : "");
            bar.setTitle(title);
            bar.setProgress(Math.max(0.01, getProgress(tr, projects.get(0), nowMs)));

            // Update viewers
            Set<Player> viewers = findViewers(townName, tr);
            for (Player p : new ArrayList<>(bar.getPlayers())) {
                if (!viewers.contains(p)) bar.removePlayer(p);
            }
            for (Player p : viewers) {
                if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
        }

        // Clean up bars for towns with no active projects
        for (String tn : unusedBars) {
            BossBar bar = townBossBars.remove(tn);
            if (bar != null) {
                bar.removeAll();
                bar.setVisible(false);
            }
        }
    }

    // ==================== Title builder (eliminates duplication) ====================

    /**
     * Build a boss bar title string. Shared by refresh timer and PlayerMoveEvent.
     */
    private String buildBarTitle(TownResearch tr, ResearchProject first, long nowMs, String suffix) {
        long effectiveMs = tr.getEffectiveDuration(first.sfKey(), first.durationMinutes(), settings) * 60000;
        long elapsed = nowMs - first.startedAt();
        int labCount = tr.countLabsResearching(first.sfKey());
        double speed = settings.calcLabSpeedMultiplier(labCount)
                * settings.getPaidSpeedLevelMultiplier(tr.getPaidSpeedLevel());

        String remainingStr = formatDuration(Math.max(0, effectiveMs - elapsed));
        String displayKey = first.sfKey();
        if (displayKey.contains(":")) displayKey = displayKey.substring(displayKey.indexOf(':') + 1);

        double progress = Math.min(1.0, (double) elapsed / effectiveMs);

        return "§b§l⚡ 研究进度 "
                + "§e" + displayKey
                + " §7| §a" + String.format("%.1f", progress * 100) + "%"
                + " §7| ⏱ " + remainingStr
                + " §7| 加速: §e" + String.format("%.1f", speed) + "x"
                + suffix;
    }

    private double getProgress(TownResearch tr, ResearchProject first, long nowMs) {
        long effectiveMs = tr.getEffectiveDuration(first.sfKey(), first.durationMinutes(), settings) * 60000;
        return Math.min(1.0, (double) (nowMs - first.startedAt()) / effectiveMs);
    }

    private static String formatDuration(long remainingMs) {
        long totalSec = remainingMs / 1000;
        if (totalSec >= 3600) {
            return String.format("%dh%02dm", totalSec / 3600, (totalSec % 3600) / 60);
        } else if (totalSec >= 60) {
            return String.format("%dm%02ds", totalSec / 60, totalSec % 60);
        }
        return totalSec + "s";
    }

    // ==================== Viewers ====================

    private Set<Player> findViewers(String townName, TownResearch tr) {
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

    // ==================== PlayerMoveEvent ====================

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
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
            if (bar == null) {
                bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
                bar.setVisible(true);
                townBossBars.put(townName, bar);
            }
            List<ResearchProject> projects = new ArrayList<>(tr.getActiveProjects().values());
            if (!projects.isEmpty()) {
                String title = buildBarTitle(tr, projects.get(0), System.currentTimeMillis(),
                        projects.size() > 1 ? " §7| §8+" + (projects.size() - 1) + " 个" : "");
                bar.setTitle(title);
                bar.setProgress(Math.max(0.01, getProgress(tr, projects.get(0), System.currentTimeMillis())));
            }
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        } else if (bar != null && bar.getPlayers().contains(player)) {
            bar.removePlayer(player);
        }
    }
}
