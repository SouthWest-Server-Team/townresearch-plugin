package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.*;

/**
 * Handles research completion checks and member grant/revoke on join/leave.
 */
public class ResearchScheduler implements Listener, Runnable {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final SlimefunBridge sfBridge;
    private final int maxLabs;
    private int taskId = -1;

    public ResearchScheduler(TownResearchPlugin plugin, SlimefunBridge sfBridge, int maxLabs) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.sfBridge = sfBridge;
        this.maxLabs = maxLabs;
    }

    public void start() {
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this, 600L, 600L).getTaskId(); // every 30 seconds
        plugin.getLogger().info("Research scheduler started (30s interval).");
    }

    public void stop() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    }

    @Override
    public void run() {
        if (!sfBridge.isAvailable()) return;

        Map<String, TownResearch> all = dataManager.loadAll(maxLabs);
        long now = System.currentTimeMillis();

        for (var entry : all.entrySet()) {
            TownResearch tr = entry.getValue();
            List<String> completed = tr.checkCompletions(now);
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
