package com.xinantown.townresearch;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.TownyCommandAddonAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;
import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class ResearchCommand implements CommandExecutor {

    private final TownResearchPlugin plugin;
    private final ResearchGuiListener guiListener;
    private final int defaultMaxLabs;

    public ResearchCommand(TownResearchPlugin plugin, ResearchGuiListener guiListener, int defaultMaxLabs) {
        this.plugin = plugin;
        this.guiListener = guiListener;
        this.defaultMaxLabs = defaultMaxLabs;
    }

    /**
     * Register subcommands under /town research via Towny addon API.
     */
    public void register() {
        TownyCommandAddonAPI.addSubCommand(
                TownyCommandAddonAPI.CommandType.TOWN,
                "research",
                this
        );
        plugin.getLogger().info("Registered /town research subcommands.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "set" -> handleSet(player);
            case "unset" -> handleUnset(player);
            case "list" -> handleList(player);
            case "start" -> handleStart(player, args);
            case "researcher" -> handleResearcher(player, args);
            default -> { sendHelp(player); yield true; }
        };
    }

    private boolean handleSet(Player player) {
        Town town = checkMayor(player);
        if (town == null) return true;

        TownResearch tr = getTownResearch(player);
        if (tr == null) return true;

        if (!tr.canAddLab()) {
            player.sendMessage("§c研究所已达上限（" + tr.getLabs().size() + "/" + defaultMaxLabs + "）！");
            return true;
        }

        TownBlock tb = TownyAPI.getInstance().getTownBlock(player.getLocation());
        if (tb == null || !tb.hasTown()) {
            player.sendMessage("§c你必须在城邦领地内标记研究所！");
            return true;
        }

        Town t = TownyAPI.getInstance().getTown(player);
        if (t == null || !t.equals(tb.getTownOrNull())) {
            player.sendMessage("§c你只能在自己的城邦领地内标记研究所！");
            return true;
        }

        ResearchLab lab = new ResearchLab(tb.getWorld().getName(), tb.getX(), tb.getZ());
        tr.addLab(lab);
        plugin.getDataManager().save(t.getName(), tr);
        player.sendMessage("§a研究所已标记在 (" + tb.getX() + ", " + tb.getZ() + ")");
        return true;
    }

    private boolean handleUnset(Player player) {
        Town town = checkMayor(player);
        if (town == null) return true;

        TownResearch tr = getTownResearch(player);
        if (tr == null) return true;

        TownBlock tb = TownyAPI.getInstance().getTownBlock(player.getLocation());
        if (tb == null) {
            player.sendMessage("§c你不在任何地块上！");
            return true;
        }

        ResearchLab lab = new ResearchLab(tb.getWorld().getName(), tb.getX(), tb.getZ());
        if (!tr.getLabs().contains(lab)) {
            player.sendMessage("§c此地块不是研究所！");
            return true;
        }

        tr.removeLab(lab);
        Town t = TownyAPI.getInstance().getTown(player);
        plugin.getDataManager().save(t.getName(), tr);
        player.sendMessage("§a研究所已取消标记 (" + tb.getX() + ", " + tb.getZ() + ")");
        return true;
    }

    private boolean handleList(Player player) {
        TownResearch tr = getTownResearch(player);
        if (tr == null) return true;

        if (tr.getLabs().isEmpty()) {
            player.sendMessage("§e当前城邦没有研究所。使用 §6/town research set §e在领地内标记。");
            return true;
        }

        player.sendMessage("§6==== 研究所列表 (" + tr.getLabs().size() + "/" + defaultMaxLabs + ") ====");
        for (ResearchLab lab : tr.getLabs()) {
            ResearchProject proj = tr.getActiveProjects().get(lab);
            String status = proj != null
                    ? "§e研究中: " + proj.sfKey()
                    : "§a空闲";
            player.sendMessage("  §7" + lab.worldName() + " (" + lab.townBlockX() + "," + lab.townBlockZ() + ") " + status);
        }

        if (!tr.getCompleted().isEmpty()) {
            player.sendMessage("§6==== 已完成科技 ====");
            for (String key : tr.getCompleted()) {
                player.sendMessage("  §a✓ " + key);
            }
        }
        return true;
    }

    private Town checkMayor(Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            player.sendMessage("§c你不属于任何城邦！");
            return null;
        }
        if (!town.hasMayor() || !town.getMayor().getUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c只有市长才能管理研究所！");
            return null;
        }
        return town;
    }

    private TownResearch getTownResearch(Player player) {
        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) {
            player.sendMessage("§c你不属于任何城邦！");
            return null;
        }

        String name = town.getName();
        TownResearch tr = plugin.getDataManager().load(name, defaultMaxLabs);
        if (tr == null) {
            tr = new TownResearch(name, defaultMaxLabs);
        }
        return tr;
    }

    private boolean handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c用法: /town research start <科技名>");
            return true;
        }

        TownResearch tr = getTownResearch(player);
        if (tr == null) return true;

        Town town = TownyAPI.getInstance().getTown(player);
        if (town != null && !guiListener.isResearcher(town.getName(), player)) {
            player.sendMessage("§c只有市长或研究员才能启动研究！");
            return true;
        }

        if (tr.getLabs().isEmpty()) {
            player.sendMessage("§c城邦没有研究所！先用 /town research set 标记。");
            return true;
        }

        String sfKey = args[1];

        if (tr.isCompleted(sfKey)) {
            player.sendMessage("§e该科技已完成研究。");
            return true;
        }

        if (tr.isResearching(sfKey)) {
            player.sendMessage("§e该科技已在研究中。");
            return true;
        }

        // Find a free lab
        var freeLab = tr.findFreeLab();

        if (freeLab == null) {
            player.sendMessage("§c所有研究所都正在使用中！等待当前项目完成。");
            return true;
        }

        // Start research with default cost/duration
        town = TownyAPI.getInstance().getTown(player);
        double balance = town.getAccount().getHoldingBalance();
        if (balance < TownResearchPlugin.BASE_RESEARCH_COST) {
            player.sendMessage("§c城邦银行余额不足！需要 $" +
                    String.format("%.0f", TownResearchPlugin.BASE_RESEARCH_COST));
            return true;
        }
        town.getAccount().withdraw(TownResearchPlugin.BASE_RESEARCH_COST,
                "研究项目: " + sfKey);

        tr.startProject(freeLab, sfKey, TownResearchPlugin.BASE_RESEARCH_MINUTES);
        plugin.getDataManager().save(town.getName(), tr);

        player.sendMessage("§a研究项目 §6" + sfKey + " §a已启动！费用: $" +
                String.format("%.0f", TownResearchPlugin.BASE_RESEARCH_COST));
        player.sendMessage("§7预计完成时间: §e" + TownResearchPlugin.BASE_RESEARCH_MINUTES + " §7分钟");
        return true;
    }

    private boolean handleResearcher(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /town research researcher add/remove <玩家名>");
            return true;
        }

        Town town = TownyAPI.getInstance().getTown(player);
        if (town == null) { player.sendMessage("§c你不属于任何城邦！"); return true; }

        // Only mayor can manage researchers
        if (!town.hasMayor() || !town.getMayor().getUUID().equals(player.getUniqueId())) {
            player.sendMessage("§c只有市长才能管理研究员！");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§c玩家 " + args[2] + " 不在线！");
            return true;
        }

        // Must be in same town
        Town targetTown = TownyAPI.getInstance().getTown(target);
        if (targetTown == null || !targetTown.getName().equals(town.getName())) {
            player.sendMessage("§c该玩家不是本城邦成员！");
            return true;
        }

        String op = args[1].toLowerCase();
        if (op.equals("add")) {
            guiListener.addResearcher(town.getName(), target.getUniqueId());
            player.sendMessage("§a已将 " + target.getName() + " 设为研究员。");
        } else if (op.equals("remove")) {
            guiListener.removeResearcher(town.getName(), target.getUniqueId());
            player.sendMessage("§a已移除 " + target.getName() + " 的研究员权限。");
        } else {
            player.sendMessage("§c用法: /town research researcher add/remove <玩家名>");
        }
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6==== 城邦研究所 ====");
        player.sendMessage("§6/town research set §7- 标记脚下地块为研究所");
        player.sendMessage("§6/town research unset §7- 取消研究所标记");
        player.sendMessage("§6/town research list §7- 查看研究所和研究进度");
        player.sendMessage("§6/town research start <科技> §7- 开始研究");
        player.sendMessage("§6/town research researcher add/remove <玩家> §7- 管理研究员");
    }
}
