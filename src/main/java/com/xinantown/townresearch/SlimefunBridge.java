package com.xinantown.townresearch;

import org.bukkit.entity.Player;

import java.util.logging.Logger;

/**
 * Reflection-based bridge to Slimefun API, avoiding compile-time dependency.
 */
public class SlimefunBridge {

    private final Logger logger;
    private boolean available = false;

    public SlimefunBridge(Logger logger) {
        this.logger = logger;
        try {
            Class.forName("io.github.thebusybiscuit.slimefun4.api.SlimefunAddon");
            available = true;
            logger.info("Slimefun detected — research integration enabled.");
        } catch (ClassNotFoundException e) {
            logger.warning("Slimefun not found — research integration disabled.");
        }
    }

    public boolean isAvailable() { return available; }

    public void dumpKeys() {
        if (!available) return;
        try {
            var registry = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun")
                    .getMethod("getRegistry").invoke(null);
            var researches = (java.util.List<?>) registry.getClass().getMethod("getResearches").invoke(registry);
            logger.info("[SlimefunBridge] Registered researches: " + researches.size());
            int count = 0;
            for (Object r : researches) {
                if (count++ >= 10) { logger.info("[SlimefunBridge] ... and " + (researches.size() - 10) + " more"); break; }
                String key = r.getClass().getMethod("getKey").invoke(r).toString();
                logger.info("[SlimefunBridge]   key=" + key);
            }
        } catch (Exception e) {
            logger.warning("[SlimefunBridge] Failed to dump keys: " + e.getMessage());
        }
    }

    public void setResearch(Player player, String sfKey, boolean grant) {
        if (!available) return;
        try {
            var research = getResearchByKey(sfKey);
            if (research == null) return;

            var profile = Class.forName("io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile")
                    .getMethod("get", Player.class).invoke(null, player);
            profile.getClass().getMethod("setResearched", research.getClass().getSuperclass(), boolean.class)
                    .invoke(profile, research, grant);
        } catch (Exception e) {
            logger.warning("Failed to " + (grant ? "grant" : "revoke") +
                    " research " + sfKey + " to/from " + player.getName());
        }
    }

    public void grantResearch(Player player, String sfKey) { setResearch(player, sfKey, true); }
    public void revokeResearch(Player player, String sfKey) { setResearch(player, sfKey, false); }

    public boolean exists(String sfKey) {
        if (!available) return false;
        try {
            if (getResearchByKey(sfKey) != null) return true;
            return getResearchByKey("slimefun:" + sfKey) != null;
        } catch (Exception e) { return false; }
    }

    /** Extract research key from a Slimefun guide ItemStack via Slimefun API. */
    public String extractKeyFromItem(org.bukkit.inventory.ItemStack item) {
        if (!available) return null;
        try {
            var sfItem = Class.forName("io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem")
                    .getMethod("getByItem", org.bukkit.inventory.ItemStack.class)
                    .invoke(null, item);
            if (sfItem == null) return null;
            var research = sfItem.getClass().getMethod("getResearch").invoke(sfItem);
            if (research == null) return null;
            return research.getClass().getMethod("getKey").invoke(research).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private Object getResearchByKey(String sfKey) throws Exception {
        var registry = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun")
                .getMethod("getRegistry").invoke(null);
        var researches = (java.util.List<?>) registry.getClass().getMethod("getResearches").invoke(registry);
        for (Object r : researches) {
            String key = r.getClass().getMethod("getKey").invoke(r).toString();
            // Match against full key (slimefun:xxx) or just the key part (xxx)
            if (key.equalsIgnoreCase(sfKey)) return r;
            if (key.contains(":") && key.substring(key.indexOf(':') + 1).equalsIgnoreCase(sfKey)) return r;
        }
        return null;
    }
}
