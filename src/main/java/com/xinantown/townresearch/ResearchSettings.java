package com.xinantown.townresearch;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages research-settings.yml configuration.
 * Loads configurable values for research costs, time multipliers,
 * lab acceleration, and paid speed levels.
 */
public class ResearchSettings {

    private final JavaPlugin plugin;
    private YamlConfiguration config;
    private final File configFile;

    private double baseCost;
    private long baseMinutes;
    private int maxLabs;

    private final Map<String, Double> researchMultipliers = new HashMap<>();
    private double minMultiplier = 0.1;
    private double maxMultiplier = 2.0;

    private double labSpeedMaxMultiplier;

    private int paidSpeedMaxLevel;
    private final Map<Integer, PaidSpeedLevel> paidSpeedLevels = new HashMap<>();

    private static class PaidSpeedLevel {
        final double cost;
        final double multiplier;

        PaidSpeedLevel(double cost, double multiplier) {
            this.cost = cost;
            this.multiplier = multiplier;
        }
    }

    public ResearchSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "research-settings.yml");
        load();
    }

    public void load() {
        // Save default if not exists
        if (!configFile.exists()) {
            plugin.saveResource("research-settings.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Base values
        baseCost = config.getDouble("base-cost", 500.0);
        baseMinutes = config.getLong("base-minutes", 60);
        maxLabs = config.getInt("max-labs", 5);

        // Research multipliers
        researchMultipliers.clear();
        if (config.contains("research-multipliers")) {
            for (String key : config.getConfigurationSection("research-multipliers").getKeys(false)) {
                double value = config.getDouble("research-multipliers." + key, 0.1);
                value = Math.max(minMultiplier, Math.min(maxMultiplier, value));
                researchMultipliers.put(key, value);
            }
        }

        // Lab speed
        labSpeedMaxMultiplier = config.getDouble("lab-speed.max-multiplier", 5.0);

        // Paid speed
        paidSpeedLevels.clear();
        paidSpeedMaxLevel = config.getInt("paid-speed.max-level", 10);
        if (config.contains("paid-speed.levels")) {
            for (String levelStr : config.getConfigurationSection("paid-speed.levels").getKeys(false)) {
                int level = Integer.parseInt(levelStr);
                double cost = config.getDouble("paid-speed.levels." + levelStr + ".cost");
                double multiplier = config.getDouble("paid-speed.levels." + levelStr + ".multiplier");
                paidSpeedLevels.put(level, new PaidSpeedLevel(cost, multiplier));
            }
        }
    }

    public void reload() {
        load();
    }

    // === Base values ===

    public double getBaseCost() {
        return baseCost;
    }

    public long getBaseMinutes() {
        return baseMinutes;
    }

    public int getMaxLabs() {
        return maxLabs;
    }

    // === Research multipliers ===

    /**
     * Get the research time multiplier for a specific tech.
     * Default is 0.1 if not configured.
     * Clamped between minMultiplier (0.1) and maxMultiplier (2.0).
     */
    public double getResearchMultiplier(String sfKey) {
        Double value = researchMultipliers.get(sfKey);
        if (value == null) {
            // Try matching without namespace prefix
            if (sfKey.contains(":")) {
                String keyPart = sfKey.substring(sfKey.indexOf(':') + 1);
                for (Map.Entry<String, Double> entry : researchMultipliers.entrySet()) {
                    if (entry.getKey().endsWith(":" + keyPart) || entry.getKey().equalsIgnoreCase(keyPart)) {
                        return entry.getValue();
                    }
                }
            }
            return 0.1; // default multiplier for unconfigured techs
        }
        return Math.max(minMultiplier, Math.min(maxMultiplier, value));
    }

    public double getMinMultiplier() {
        return minMultiplier;
    }

    public double getMaxMultiplier() {
        return maxMultiplier;
    }

    // === Lab speed ===

    public double getLabSpeedMaxMultiplier() {
        return labSpeedMaxMultiplier;
    }

    // === Paid speed ===

    public int getPaidSpeedMaxLevel() {
        return paidSpeedMaxLevel;
    }

    public double getPaidSpeedLevelCost(int level) {
        PaidSpeedLevel psl = paidSpeedLevels.get(level);
        return psl != null ? psl.cost : Double.MAX_VALUE;
    }

    public double getPaidSpeedLevelMultiplier(int level) {
        PaidSpeedLevel psl = paidSpeedLevels.get(level);
        return psl != null ? psl.multiplier : 1.0;
    }

    /**
     * Calculate the lab speed multiplier based on number of labs researching the same tech.
     * Linear interpolation: 1 lab = 1x, maxLabs labs = getLabSpeedMaxMultiplier()
     */
    public double calcLabSpeedMultiplier(int labCount) {
        if (labCount <= 1) return 1.0;
        if (labCount >= 5) return labSpeedMaxMultiplier;
        // Linear: 1=1x, 2=1+(max-1)/4, 3=1+2*(max-1)/4, 4=1+3*(max-1)/4, 5=max
        return 1.0 + (labCount - 1) / 4.0 * (labSpeedMaxMultiplier - 1.0);
    }
}
