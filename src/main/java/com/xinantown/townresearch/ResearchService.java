package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.object.Town;
import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.entity.Player;

/**
 * Shared service for starting research projects.
 * Command and GUI both delegate here instead of duplicating logic.
 */
public class ResearchService {

    private final TownDataManager dataManager;
    private final SlimefunBridge sfBridge;
    private final int maxLabs;

    public ResearchService(TownDataManager dataManager, SlimefunBridge sfBridge, int maxLabs) {
        this.dataManager = dataManager;
        this.sfBridge = sfBridge;
        this.maxLabs = maxLabs;
    }

    /**
     * Attempt to start a research project. Returns null on success, or an error message.
     */
    public String startResearch(Town town, Player player, String sfKey) {
        TownResearch tr = dataManager.load(town.getName(), maxLabs);
        if (tr == null) tr = new TownResearch(town.getName(), maxLabs);

        if (!sfBridge.exists(sfKey)) return "§c未知的科技: " + sfKey;
        if (tr.isCompleted(sfKey)) return "§e该科技已完成研究。";
        if (tr.isResearching(sfKey)) return "§e该科技已在研究中。";

        if (tr.getLabs().isEmpty()) return "§c城邦没有研究所！先用 /town research set 标记。";

        ResearchLab freeLab = tr.findFreeLab();
        if (freeLab == null) return "§c所有研究所都正在使用中！等待当前项目完成。";

        double cost = TownResearchPlugin.BASE_RESEARCH_COST;
        double balance = town.getAccount().getHoldingBalance();
        if (balance < cost) {
            return "§c城邦银行余额不足！需要 $" + String.format("%.0f", cost);
        }

        town.getAccount().withdraw(cost, "研究项目: " + sfKey);
        tr.startProject(freeLab, sfKey, TownResearchPlugin.BASE_RESEARCH_MINUTES);
        dataManager.save(town.getName(), tr);

        return null; // success
    }

    public static String successMessage(String sfKey) {
        return "§a研究项目 §6" + sfKey + " §a已启动！费用: $" +
                String.format("%.0f", TownResearchPlugin.BASE_RESEARCH_COST) +
                "\n§7预计完成时间: §e" + TownResearchPlugin.BASE_RESEARCH_MINUTES + " §7分钟";
    }
}
