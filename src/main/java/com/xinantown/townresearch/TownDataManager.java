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
        saveToDisk(townName, tr);
        cache.put(townName.toLowerCase(), tr);
    }

    private File fileFor(String townName) {
        return new File(dataFolder, townName.toLowerCase() + ".yml");
    }

    private void saveToDisk(String townName, TownResearch tr) {
        File current = fileFor(townName);
        if (current.exists()) loadFromConfig(townName, readConfig(current), Integer.MAX_VALUE);
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("storage_version", 1);
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

        List<Map<String, Object>> pausedList = new ArrayList<>();
        for (var e : tr.getPausedProjects().entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lab_world", e.getKey().worldName());
            m.put("lab_x", e.getKey().townBlockX());
            m.put("lab_z", e.getKey().townBlockZ());
            m.put("key", e.getValue().sfKey());
            m.put("started", e.getValue().startedAt());
            m.put("duration", e.getValue().durationMinutes());
            pausedList.add(m);
        }
        cfg.set("paused_projects", pausedList);

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
            YamlConfiguration cfg = readConfig(f);
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
        YamlConfiguration cfg = readConfig(f);
        return loadFromConfig(townName, cfg, maxLabs);
    }

    private YamlConfiguration readConfig(File file) {
        var cfg = new YamlConfiguration();
        try { cfg.load(file); }
        catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("Research file unreadable; preserved: " + file.getName(), e);
        }
        return cfg;
    }

    private TownResearch loadFromConfig(String townName, YamlConfiguration cfg, int maxLabs) {
        Object version = cfg.get("storage_version");
        if (version != null && (!(version instanceof Number n) || n.doubleValue() != 1)) {
            throw new IllegalStateException("Unsupported research storage_version for " + townName);
        }
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

        Object paused = cfg.get("paused_projects");
        if (paused != null) {
            if (!(paused instanceof List<?> records)) throw new IllegalStateException("Invalid paused_projects for " + townName);
            for (Object record : records) {
                if (!(record instanceof Map<?, ?> m)
                        || !(m.get("lab_world") instanceof String world)
                        || !(m.get("key") instanceof String key)) {
                    throw new IllegalStateException("Invalid paused project for " + townName);
                }
                ResearchLab lab = new ResearchLab(world, strictInt(m.get("lab_x")), strictInt(m.get("lab_z")));
                tr.restorePausedProject(lab, key, strictLong(m.get("started")), strictLong(m.get("duration")));
            }
        }
        return tr;
    }

    private long strictLong(Object value) {
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue() != n.longValue()) {
            throw new IllegalStateException("Invalid paused project integer");
        }
        return n.longValue();
    }

    private int strictInt(Object value) {
        long number = strictLong(value);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw new IllegalStateException("Paused lab coordinate out of range");
        return (int) number;
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
