package com.xinantown.townresearch;

import org.bukkit.entity.Player;

import java.util.Set;
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

    private Object getResearchByKey(String sfKey) throws Exception {
        var registry = Class.forName("io.github.thebusybiscuit.slimefun4.implementation.Slimefun")
                .getMethod("getRegistry").invoke(null);
        var researches = (Set<?>) registry.getClass().getMethod("getResearches").invoke(registry);
        for (Object r : researches) {
            String key = r.getClass().getMethod("getKey").invoke(r).toString();
            if (key.equals(sfKey)) return r;
        }
        return null;
    }
}
