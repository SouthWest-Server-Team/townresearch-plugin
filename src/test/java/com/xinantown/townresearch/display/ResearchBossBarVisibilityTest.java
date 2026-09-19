package com.xinantown.townresearch.display;

import com.xinantown.townresearch.model.ResearchLab;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks plot-based BossBar visibility: standing on a lab TownBlock is enough;
 * town residency / membership must not gate show.
 */
class ResearchBossBarVisibilityTest {

    private static final ResearchLab LAB = new ResearchLab("world", 10, 20);

    @Test
    void matchesAnyLab_whenStandingOnLabPlot() {
        assertTrue(ResearchBossBarVisibility.matchesAnyLab("world", 10, 20, List.of(LAB)));
        assertFalse(ResearchBossBarVisibility.matchesAnyLab("world", 11, 20, List.of(LAB)));
        assertFalse(ResearchBossBarVisibility.matchesAnyLab("other", 10, 20, List.of(LAB)));
    }

    @Test
    void includeOnlineViewer_requiresOnlineAndNearLab_notResidency() {
        // residency is intentionally not a parameter — visibility is plot-based
        assertTrue(ResearchBossBarVisibility.includeOnlineViewer(true, true));
        assertFalse(ResearchBossBarVisibility.includeOnlineViewer(false, true));
        assertFalse(ResearchBossBarVisibility.includeOnlineViewer(true, false));
    }

    @Test
    void resolveActiveTownForLocation_usesPlotTown_evenWhenMembershipMissingOrDifferent() {
        Predicate<String> hasActive = town -> "SpawnTown".equals(town);

        // townless player standing on SpawnTown lab plot
        assertEquals(Optional.of("SpawnTown"),
                ResearchBossBarVisibility.resolveActiveTownForLocation(
                        null, "SpawnTown", hasActive));

        // member of OtherTown standing on SpawnTown lab plot
        assertEquals(Optional.of("SpawnTown"),
                ResearchBossBarVisibility.resolveActiveTownForLocation(
                        "OtherTown", "SpawnTown", hasActive));

        // no plot town
        assertEquals(Optional.empty(),
                ResearchBossBarVisibility.resolveActiveTownForLocation(
                        "OtherTown", null, hasActive));

        // plot town has no active research
        assertEquals(Optional.empty(),
                ResearchBossBarVisibility.resolveActiveTownForLocation(
                        null, "IdleTown", hasActive));
    }

    @Test
    void selectNearLabPlayerIds_includesNonResidentStandingOnLab() {
        UUID resident = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID visitor = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID farAway = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        Set<UUID> selected = ResearchBossBarVisibility.selectNearLabPlayerIds(
                List.of(
                        new ResearchBossBarVisibility.OnlinePresence(resident, true),
                        new ResearchBossBarVisibility.OnlinePresence(visitor, true),
                        new ResearchBossBarVisibility.OnlinePresence(farAway, false)
                )
        );

        assertEquals(Set.of(resident, visitor), selected);
        assertFalse(selected.contains(farAway));
    }

    @Test
    void debugLogging_defaultsDisabled() {
        assertFalse(ResearchBossBarVisibility.debugLoggingEnabled(false));
        assertTrue(ResearchBossBarVisibility.debugLoggingEnabled(true));
    }
}
