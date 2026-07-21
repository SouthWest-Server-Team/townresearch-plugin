package com.xinantown.townresearch;

import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.TownResearch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TownDataManagerTest {

    @TempDir
    Path tempDir;

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
