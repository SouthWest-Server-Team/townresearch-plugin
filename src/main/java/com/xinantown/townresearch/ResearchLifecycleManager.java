package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the full lifecycle of town research:
 * <ul>
 *   <li>30s completion checks — grants completed tech to town residents</li>
 *   <li>PlayerJoinEvent — grants existing tech + processes pending revokes</li>
 *   <li>TownRemoveResidentEvent — revokes tech from leaving residents</li>
 * </ul>
 */
public class ResearchLifecycleManager implements Listener {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final SlimefunBridge sfBridge;
    private final ResearchSettings settings;
    private final int maxLabs;

    public ResearchLifecycleManager(TownResearchPlugin plugin, SlimefunBridge sfBridge,
                                     ResearchSettings settings) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
        this.sfBridge = sfBridge;
        this.settings = settings;
        this.maxLabs = settings.getMaxLabs();
    }

    /**
     * Check all towns for completed research projects and grant rewards.
     * Called by the 30-second timer.
     */
    public void checkCompletions() {
        if (!sfBridge.isAvailable()) return;

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

    // === Public API ===

    /** Grant all completed research for the player's town. */
    public void grantTownResearch(Player player) {
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
