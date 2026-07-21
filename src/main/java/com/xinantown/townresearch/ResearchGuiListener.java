package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Intercepts Slimefun guide GUI clicks to enforce town-based research.
 */
public class ResearchGuiListener implements Listener {

    private final TownResearchPlugin plugin;
    private final TownDataManager dataManager;
    private final int maxLabs;

    // Town name → set of uuids who are researchers (loaded from data.yml)
    private final Map<String, Set<UUID>> researchers = new HashMap<>();

    public ResearchGuiListener(TownResearchPlugin plugin, int maxLabs) {
        this.plugin = plugin;
        this.dataManager = plugin.getDataManager();
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
        // Persist
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
        // Mayor is always a researcher
        Town town = TownyAPI.getInstance().getTown(player);
        if (town != null && town.hasMayor() && town.getMayor().getUUID().equals(player.getUniqueId())) {
            return true;
        }
        Set<UUID> set = researchers.get(townName.toLowerCase());
        return set != null && set.contains(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Detect Slimefun guide inventory by class name (avoid fragile title matching)
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null) return;
        String holderClass = inv.getHolder().getClass().getName();
        if (!holderClass.contains("slimefun") || !holderClass.contains("guide")) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        // Check if this is a research item in the guide
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            event.setCancelled(true);
            player.sendMessage("§c你需要加入一个城邦才能研究科技！");
            return;
        }

        var tr = dataManager.load(town.getName(), maxLabs);
        if (tr == null) {
            event.setCancelled(true);
            player.sendMessage("§c城邦还没有研究所！让市长用 /town research set 标记。");
            return;
        }

        if (!isResearcher(town.getName(), player)) {
            event.setCancelled(true);
            player.sendMessage("§c你需要研究员权限才能启动研究！");
            return;
        }

        // Extract Slimefun research key from item lore
        String sfKey = extractResearchKey(clicked);
        if (sfKey == null) return;

        // Check if already completed
        if (tr.isCompleted(sfKey)) {
            event.setCancelled(true);
            player.sendMessage("§e此科技已完成研究。");
            return;
        }

        // Find free lab
        var freeLab = tr.findFreeLab();

        if (freeLab == null) {
            player.sendMessage("§c所有研究所都正在使用中！");
            return;
        }

        // Start research
        double cost = TownResearchPlugin.BASE_RESEARCH_COST;
        double balance = town.getAccount().getHoldingBalance();
        if (balance < cost) {
            event.setCancelled(true);
            player.sendMessage("§c城邦银行余额不足！需要 $" + String.format("%.0f", cost));
            return;
        }
        town.getAccount().withdraw(cost, "研究项目: " + sfKey);

        tr.startProject(freeLab, sfKey, TownResearchPlugin.BASE_RESEARCH_MINUTES);
        dataManager.save(town.getName(), tr);

        player.closeInventory();
        player.sendMessage("§a研究项目 §6" + sfKey + " §a已启动！费用: $" +
                String.format("%.0f", cost));
    }

    private String extractResearchKey(ItemStack item) {
        if (item.getItemMeta() == null || item.getItemMeta().getLore() == null) return null;
        for (String line : item.getItemMeta().getLore()) {
            String plain = org.bukkit.ChatColor.stripColor(line);
            if (plain.startsWith("Key:") || plain.startsWith("ID:")) {
                String[] parts = plain.split(":\\s*", 2);
                if (parts.length == 2) return parts[1].trim();
            }
        }
        return null;
    }
}
