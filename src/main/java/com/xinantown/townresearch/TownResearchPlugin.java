package com.xinantown.townresearch;

import org.bukkit.plugin.java.JavaPlugin;

public class TownResearchPlugin extends JavaPlugin {

    public static final double BASE_RESEARCH_COST = 500.0;
    public static final long BASE_RESEARCH_MINUTES = 60;

    private TownDataManager dataManager;
    private ResearchLifecycleManager lifecycleManager;
    private ResearchBossBarManager bossBarManager;
    private ResearchSettings researchSettings;
    private int maxLabs = 5;
    private int lifecycleTaskId = -1;
    private int displayTaskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maxLabs = getConfig().getInt("max-labs", 5);

        dataManager = new TownDataManager(new java.io.File(getDataFolder(), "data"), getLogger());

        researchSettings = new ResearchSettings(this);

        SlimefunBridge sfBridge = new SlimefunBridge(getLogger());
        sfBridge.dumpKeys();

        ResearchService service = new ResearchService(dataManager, sfBridge, researchSettings);
        ResearchGuiListener guiListener = new ResearchGuiListener(dataManager, maxLabs);
        new ResearchCommand(this, guiListener, sfBridge, researchSettings).register();

        // Load persisted researchers into memory
        for (String townName : dataManager.loadAll(maxLabs).keySet()) {
            guiListener.loadResearchers(townName);
        }

        // Research lifecycle — completion checks + player join/leave events
        lifecycleManager = new ResearchLifecycleManager(this, sfBridge, researchSettings);
        getServer().getPluginManager().registerEvents(lifecycleManager, this);
        lifecycleTaskId = getServer().getScheduler().runTaskTimer(
                this, lifecycleManager::checkCompletions, 600L, 600L).getTaskId();

        // Boss bar progress display — plot-only periodic scan (no PlayerMoveEvent)
        bossBarManager = new ResearchBossBarManager(this, researchSettings);
        getServer().getPluginManager().registerEvents(bossBarManager, this);
        displayTaskId = getServer().getScheduler().runTaskTimer(
                this, bossBarManager::refreshBars, 20L, 20L).getTaskId();

        // Intercept Slimefun PlayerResearchEvent (replaces GUI click interception)
        new ResearchEventListener(this, service, guiListener).register();

        getLogger().info("TownResearch enabled. Max labs per town: " + maxLabs);
    }

    @Override
    public void onDisable() {
        if (lifecycleTaskId != -1) getServer().getScheduler().cancelTask(lifecycleTaskId);
        if (displayTaskId != -1) getServer().getScheduler().cancelTask(displayTaskId);
        if (bossBarManager != null) bossBarManager.cleanup();
        getLogger().info("TownResearch disabled.");
    }

    public TownDataManager getDataManager() { return dataManager; }
    public ResearchSettings getResearchSettings() { return researchSettings; }
}
