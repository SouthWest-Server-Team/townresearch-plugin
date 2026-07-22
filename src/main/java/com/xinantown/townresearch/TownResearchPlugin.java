package com.xinantown.townresearch;

import org.bukkit.plugin.java.JavaPlugin;

public class TownResearchPlugin extends JavaPlugin {

    public static final double BASE_RESEARCH_COST = 500.0;
    public static final long BASE_RESEARCH_MINUTES = 60;

    private TownDataManager dataManager;
    private ResearchScheduler scheduler;
    private int maxLabs = 5;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maxLabs = getConfig().getInt("max-labs", 5);

        dataManager = new TownDataManager(new java.io.File(getDataFolder(), "data"), getLogger());

        SlimefunBridge sfBridge = new SlimefunBridge(getLogger());
        sfBridge.dumpKeys();

        ResearchService service = new ResearchService(dataManager, sfBridge, maxLabs);
        ResearchGuiListener guiListener = new ResearchGuiListener(dataManager, maxLabs);
        new ResearchCommand(this, guiListener, sfBridge, maxLabs).register();

        // Load persisted researchers into memory
        for (String townName : dataManager.loadAll(maxLabs).keySet()) {
            guiListener.loadResearchers(townName);
        }

        scheduler = new ResearchScheduler(this, sfBridge, maxLabs);
        scheduler.start();
        getServer().getPluginManager().registerEvents(scheduler, this);

        // Intercept Slimefun PlayerResearchEvent (replaces GUI click interception)
        new ResearchEventListener(this, service, guiListener).register();

        getLogger().info("TownResearch enabled. Max labs per town: " + maxLabs);
    }

    @Override
    public void onDisable() {
        if (scheduler != null) scheduler.stop();
        getLogger().info("TownResearch disabled.");
    }

    public TownDataManager getDataManager() { return dataManager; }
}
