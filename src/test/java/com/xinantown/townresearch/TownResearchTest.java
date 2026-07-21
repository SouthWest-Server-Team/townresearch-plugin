package com.xinantown.townresearch;

import com.xinantown.townresearch.model.ResearchLab;
import com.xinantown.townresearch.model.ResearchProject;
import com.xinantown.townresearch.model.TownResearch;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TownResearchTest {

    @Test
    void newTownResearch_hasNoLabs() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        assertTrue(tr.getLabs().isEmpty());
    }

    @Test
    void canAddResearchLab() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        assertEquals(1, tr.getLabs().size());
        assertTrue(tr.getLabs().contains(lab));
    }

    @Test
    void cannotExceedMaxLabs() {
        TownResearch tr = new TownResearch("SpawnTown", 2);
        tr.addLab(new ResearchLab("world", 100, 64));
        tr.addLab(new ResearchLab("world", 101, 64));
        assertFalse(tr.canAddLab());
        assertThrows(IllegalStateException.class, () ->
                tr.addLab(new ResearchLab("world", 102, 64)));
    }

    @Test
    void removeLab_pausesActiveProject() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.startProject(lab, "sf:test", 60);
        tr.removeLab(lab);
        assertTrue(tr.getActiveProjects().isEmpty());
        assertTrue(tr.getLabs().isEmpty());
    }

    @Test
    void reAddLab_resumesPausedProject() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.startProject(lab, "sf:test", 60);
        tr.removeLab(lab);
        tr.addLab(lab);
        assertFalse(tr.getActiveProjects().isEmpty());
        assertEquals("sf:test", tr.getActiveProjects().get(lab).sfKey());
    }

    @Test
    void canRemoveResearchLab() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.removeLab(lab);
        assertTrue(tr.getLabs().isEmpty());
    }

    @Test
    void canStartResearchProject() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.startProject(lab, "slimefun:walking_sticks", 60);
        assertTrue(tr.getActiveProjects().containsKey(lab));
        assertEquals("slimefun:walking_sticks", tr.getActiveProjects().get(lab).sfKey());
    }

    @Test
    void cannotStartProjectOnUnregisteredLab() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        assertThrows(IllegalArgumentException.class, () ->
                tr.startProject(lab, "slimefun:walking_sticks", 60));
    }

    @Test
    void cannotStartDuplicateProject() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.startProject(lab, "slimefun:walking_sticks", 60);
        assertThrows(IllegalStateException.class, () ->
                tr.startProject(lab, "slimefun:walking_sticks", 60));
    }

    @Test
    void completedResearchIsTracked() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        tr.addCompleted("slimefun:walking_sticks");
        assertTrue(tr.isCompleted("slimefun:walking_sticks"));
        assertFalse(tr.isCompleted("slimefun:armor_forge"));
    }

    @Test
    void multipleLabsAccelerateResearch() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab1 = new ResearchLab("world", 100, 64);
        ResearchLab lab2 = new ResearchLab("world", 101, 64);
        tr.addLab(lab1);
        tr.addLab(lab2);
        tr.startProject(lab1, "slimefun:walking_sticks", 120);
        tr.startProject(lab2, "slimefun:walking_sticks", 120);

        long duration = tr.getEffectiveDuration("slimefun:walking_sticks", 120);
        assertEquals(60, duration); // 120 / 2 labs
    }

    @Test
    void singleLabNoAcceleration() {
        TownResearch tr = new TownResearch("SpawnTown", 5);
        ResearchLab lab = new ResearchLab("world", 100, 64);
        tr.addLab(lab);
        tr.startProject(lab, "slimefun:walking_sticks", 120);

        long duration = tr.getEffectiveDuration("slimefun:walking_sticks", 120);
        assertEquals(120, duration);
    }
}
