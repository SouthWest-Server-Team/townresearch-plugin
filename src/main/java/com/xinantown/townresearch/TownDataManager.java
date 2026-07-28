package com.xinantown.townresearch;

import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * YAML persistence for town research data. One file per town under data/.
 */
public class TownDataManager {

    private final File dataFolder;
    private final java.util.logging.Logger logger;
    private final Map<String, TownResearch> cache = new java.util.LinkedHashMap<>();

    public TownDataManager(File dataFolder, java.util.logging.Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    /** Load with caching — subsequent calls within same tick return cached instance. */
    public TownResearch load(String townName, int maxLabs) {
        String key = townName.toLowerCase();
        TownResearch cached = cache.get(key);
        if (cached != null) return cached;

        TownResearch tr = loadFromDisk(townName, maxLabs);
        if (tr != null) cache.put(key, tr);
        return tr;
    }

    public void save(String townName, TownResearch tr) {
        cache.put(townName.toLowerCase(), tr);
        saveToDisk(townName, tr);
    }

    private File fileFor(String townName) {
        return new File(dataFolder, townName.toLowerCase() + ".yml");
    }

    private void saveToDisk(String townName, TownResearch tr) {
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> labList = new ArrayList<>();
        for (ResearchLab lab : tr.getLabs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("world", lab.worldName());
            m.put("x", lab.townBlockX());
            m.put("z", lab.townBlockZ());
            labList.add(m);
        }
        cfg.set("town_name", townName);
        cfg.set("labs", labList);
        cfg.set("completed", new ArrayList<>(tr.getCompleted()));

        List<Map<String, Object>> projectList = new ArrayList<>();
        for (var e : tr.getActiveProjects().entrySet()) {
            ResearchLab lab = e.getKey();
            ResearchProject p = e.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lab_world", lab.worldName());
            m.put("lab_x", lab.townBlockX());
            m.put("lab_z", lab.townBlockZ());
            m.put("key", p.sfKey());
            m.put("started", p.startedAt());
            m.put("duration", p.durationMinutes());
            projectList.add(m);
        }
        cfg.set("active_projects", projectList);

        List<String> researcherList = new ArrayList<>();
        for (UUID id : tr.getResearchers()) researcherList.add(id.toString());
        cfg.set("researchers", researcherList);

        List<String> revokeList = new ArrayList<>();
        for (UUID id : tr.getPendingRevokes()) revokeList.add(id.toString());
        cfg.set("pending_revokes", revokeList);

        cfg.set("paid_speed_level", tr.getPaidSpeedLevel());

        try { cfg.save(fileFor(townName)); } catch (IOException e) {
            logger.log(java.util.logging.Level.SEVERE,
                    "Failed to save research data for " + townName, e);
        }
    }

    public Map<String, TownResearch> loadAll(int defaultMaxLabs) {
        Map<String, TownResearch> result = new LinkedHashMap<>();
        File[] files = dataFolder.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return result;
        for (File f : files) {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String name = cfg.getString("town_name");
            if (name == null) continue;
            TownResearch tr = loadFromConfig(name, cfg, defaultMaxLabs);
            if (tr != null) result.put(name, tr);
        }
        return result;
    }

    private TownResearch loadFromDisk(String townName, int maxLabs) {
        File f = fileFor(townName);
        if (!f.exists()) return null;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return loadFromConfig(townName, cfg, maxLabs);
    }

    private TownResearch loadFromConfig(String townName, YamlConfiguration cfg, int maxLabs) {
        TownResearch tr = new TownResearch(townName, maxLabs);

        List<Map<?, ?>> labList = cfg.getMapList("labs");
        if (labList != null) {
            for (Map<?, ?> m : labList) {
                if (!tr.canAddLab()) break; // Graceful overflow — skip extra labs
                tr.addLab(new ResearchLab(
                        String.valueOf(m.get("world")),
                        parseInt(m.get("x")), parseInt(m.get("z"))));
            }
        }

        List<String> completed = cfg.getStringList("completed");
        if (completed != null) completed.forEach(tr::addCompleted);

        List<String> researcherList = cfg.getStringList("researchers");
        if (researcherList != null) {
            for (String s : researcherList) tr.addResearcher(java.util.UUID.fromString(s));
        }

        List<String> revokeList = cfg.getStringList("pending_revokes");
        if (revokeList != null) {
            for (String s : revokeList) tr.addPendingRevoke(java.util.UUID.fromString(s));
        }

        tr.setPaidSpeedLevel(cfg.getInt("paid_speed_level", 0));

        List<Map<?, ?>> projList = cfg.getMapList("active_projects");
        if (projList != null) {
            for (Map<?, ?> m : projList) {
                ResearchLab lab = new ResearchLab(
                        String.valueOf(m.get("lab_world")),
                        parseInt(m.get("lab_x")), parseInt(m.get("lab_z")));
                if (tr.getLabs().contains(lab)) {
                    tr.resumeProject(lab,
                            String.valueOf(m.get("key")),
                            parseLong(m.get("started")),
                            parseLong(m.get("duration")));
                }
            }
        }

        return tr;
    }

    private int parseInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private long parseLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }
}
