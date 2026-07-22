package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Manages researcher permissions per town, persisted to data.yml.
 */
public class ResearchGuiListener {

    private final TownDataManager dataManager;
    private final int maxLabs;
    private final Map<String, Set<UUID>> researchers = new HashMap<>();

    public ResearchGuiListener(TownDataManager dataManager, int maxLabs) {
        this.dataManager = dataManager;
        this.maxLabs = maxLabs;
    }

    public void loadResearchers(String townName) {
        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr != null && !tr.getResearchers().isEmpty()) {
            researchers.put(townName.toLowerCase(), new HashSet<>(tr.getResearchers()));
        }
    }

    public void addResearcher(String townName, UUID uuid) {
        Set<UUID> set = researchers.computeIfAbsent(townName.toLowerCase(), k -> new HashSet<>());
        set.add(uuid);
        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr == null) tr = new TownResearch(townName, maxLabs);
        tr.addResearcher(uuid);
        dataManager.save(townName, tr);
    }

    public void removeResearcher(String townName, UUID uuid) {
        Set<UUID> set = researchers.get(townName.toLowerCase());
        if (set != null) set.remove(uuid);
        TownResearch tr = dataManager.load(townName, maxLabs);
        if (tr == null) tr = new TownResearch(townName, maxLabs);
        tr.removeResearcher(uuid);
        dataManager.save(townName, tr);
    }

    public boolean isResearcher(String townName, Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        if (town != null && town.hasMayor() && town.getMayor().getUUID().equals(player.getUniqueId())) return true;
        Set<UUID> set = researchers.get(townName.toLowerCase());
        return set != null && set.contains(player.getUniqueId());
    }
}
