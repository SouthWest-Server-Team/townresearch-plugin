package com.xinantown.townresearch;

import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.TownResearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.Map;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class TownDataManagerTest {

    @TempDir
    Path tempDir;

    private TownDataManager fresh() {
        return new TownDataManager(tempDir.toFile(), Logger.getLogger("test"));
    }

    @Test
    void pausedAndActiveProjectsSurviveRealYamlRestart() {
        var tr = new TownResearch("Town", 5);
        var pausedLab = new ResearchLab("world", -3, 7);
        var activeLab = new ResearchLab("world", 9, 10);
        tr.addLab(pausedLab);
        tr.addLab(activeLab);
        tr.resumeProject(pausedLab, "sf:paused", 1234L, 120L);
        tr.pauseProject(pausedLab);
        tr.resumeProject(activeLab, "sf:active", 5678L, 60L);
        fresh().save("Town", tr);
        var loaded = fresh().load("Town", 5);
        assertEquals(tr.getPausedProjects(), loaded.getPausedProjects());
        assertEquals(tr.getActiveProjects(), loaded.getActiveProjects());
        assertTrue(loaded.isPaused(pausedLab));
    }

    @Test
    void removedLabImplicitPauseSurvivesRestart() {
        var tr = new TownResearch("Town", 5);
        var lab = new ResearchLab("world", 1, 2);
        tr.addLab(lab);
        tr.resumeProject(lab, "sf:removed", 999, 30);
        tr.removeLab(lab);
        fresh().save("Town", tr);
        var loaded = fresh().load("Town", 5);
        assertFalse(loaded.getLabs().contains(lab));
        assertEquals(tr.getPausedProjects(), loaded.getPausedProjects());
    }

    @Test
    void legacyMissingPausedFieldDoesNotInventProjects() throws Exception {
        Files.writeString(tempDir.resolve("town.yml"), "town_name: Town\nlabs: []\nactive_projects: []\n");
        assertTrue(fresh().load("Town", 5).getPausedProjects().isEmpty());
    }

    @Test
    void unknownVersionAndMalformedPausedRecordsPreserveFile() throws Exception {
        for (String yaml : java.util.List.of(
                "town_name: Town\nstorage_version: 2\npaused_projects: []\n",
                "town_name: [broken\n",
                "town_name: Town\nstorage_version: 1\npaused_projects: broken\n",
                "town_name: Town\nstorage_version: 1\npaused_projects:\n- {lab_world: world, lab_x: 1, lab_z: 2, key: sf:x, started: bad, duration: 30}\n",
                "town_name: Town\nstorage_version: 1\npaused_projects:\n- &p {lab_world: world, lab_x: 1, lab_z: 2, key: sf:x, started: 0, duration: 30}\n- *p\n")) {
            Files.writeString(tempDir.resolve("town.yml"), yaml);
            assertThrows(RuntimeException.class, () -> fresh().load("Town", 5));
            assertThrows(RuntimeException.class, () -> fresh().save("Town", new TownResearch("Town", 5)));
            assertEquals(yaml, Files.readString(tempDir.resolve("town.yml")));
        }
    }

    @Test
    void saveAndLoad_roundtrip() {
        TownDataManager mgr = new TownDataManager(tempDir.toFile(), Logger.getLogger("test"));
        TownResearch tr = new TownResearch("SpawnTown", 5);
        tr.addLab(new ResearchLab("world", 100, 64));
        tr.addLab(new ResearchLab("world", 200, 64));
        tr.addCompleted("sf:test_item");
        mgr.save("SpawnTown", tr);

        TownResearch loaded = mgr.load("SpawnTown", 5);
        assertNotNull(loaded);
        assertEquals(2, loaded.getLabs().size());
        assertTrue(loaded.isCompleted("sf:test_item"));
    }

    @Test
    void loadNonExistent_returnsNull() {
        TownDataManager mgr = new TownDataManager(tempDir.toFile(), Logger.getLogger("test"));
        assertNull(mgr.load("GhostTown", 5));
    }

    @Test
    void saveAndLoadMultipleTowns() {
        TownDataManager mgr = new TownDataManager(tempDir.toFile(), Logger.getLogger("test"));
        TownResearch t1 = new TownResearch("TownA", 3);
        t1.addCompleted("sf:a");
        TownResearch t2 = new TownResearch("TownB", 5);
        t2.addCompleted("sf:b");

        mgr.save("TownA", t1);
        mgr.save("TownB", t2);

        Map<String, TownResearch> all = mgr.loadAll(5);
        assertEquals(2, all.size());
        assertTrue(all.get("TownA").isCompleted("sf:a"));
        assertTrue(all.get("TownB").isCompleted("sf:b"));
    }
}
