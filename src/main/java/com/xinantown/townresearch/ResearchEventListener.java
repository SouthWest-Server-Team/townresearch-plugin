package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.RegisteredListener;

import java.util.logging.Logger;

/**
 * Intercepts Slimefun's PlayerResearchEvent.
 * Registered dynamically because the event class isn't available at compile time.
 */
public class ResearchEventListener {

    private final TownResearchPlugin plugin;
    private final ResearchService service;
    private final ResearchGuiListener guiListener;
    private final Logger logger;

    public ResearchEventListener(TownResearchPlugin plugin, ResearchService service,
                                  ResearchGuiListener guiListener) {
        this.plugin = plugin;
        this.service = service;
        this.guiListener = guiListener;
        this.logger = plugin.getLogger();
    }

    public void register() {
        try {
            Class<? extends Event> eventClass = Class.forName(
                    "io.github.thebusybiscuit.slimefun4.api.events.PlayerPreResearchEvent")
                    .asSubclass(Event.class);
            EventPriority priority = EventPriority.LOWEST;
            EventExecutor executor = (listener, event) -> onResearch(event);
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, new Listener(){}, priority, executor, plugin);
            logger.info("Registered PlayerPreResearchEvent listener.");
        } catch (ClassNotFoundException e) {
            logger.warning("PlayerResearchEvent not found — research interception disabled.");
        }
    }

    private void onResearch(Event event) {
        if (!(event instanceof Cancellable cancellable)) return;

        try {
            Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
            Object research = event.getClass().getMethod("getResearch").invoke(event);
            String sfKey = research.getClass().getMethod("getKey").invoke(research).toString();

            cancellable.setCancelled(true);

            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null) {
                player.sendMessage("§c你需要加入一个城邦才能研究科技！");
                return;
            }

            if (!guiListener.isResearcher(town.getName(), player)) {
                player.sendMessage("§c只有市长或研究员才能启动研究！");
                return;
            }

            String error = service.startResearch(town, player, sfKey);
            if (error != null) {
                player.sendMessage(error);
                return;
            }

            player.closeInventory();
            player.sendMessage(ResearchService.successMessage(sfKey));
        } catch (Exception e) {
            logger.warning("PlayerResearchEvent handler failed: " + e.getMessage());
        }
    }
}
