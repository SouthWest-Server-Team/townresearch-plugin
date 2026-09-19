package com.xinantown.townresearch.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchBossBarPresentationTest {

    @Test
    void shouldDisplay_requiresActiveProjectsAndViewerPresence() {
        assertFalse(ResearchBossBarPresentation.shouldDisplay(false, true));
        assertFalse(ResearchBossBarPresentation.shouldDisplay(true, false));
        assertTrue(ResearchBossBarPresentation.shouldDisplay(true, true));
        // viewerPresent is plot presence, not town residency
        assertTrue(ResearchBossBarPresentation.shouldDisplay(true, true),
                "active research + standing on lab plot must display regardless of membership");
    }

    @Test
    void build_formatsTitleProgressAndDisplayKey() {
        // effective duration = 1 minute; elapsed 30s → 50%
        ResearchBossBarPresentation.Snapshot snap = ResearchBossBarPresentation.build(
                "slimefun:walking_sticks",
                0L,
                1L,
                1,
                1.0,
                30_000L,
                0
        );

        assertEquals("walking_sticks", snap.displayKey());
        assertEquals(0.5, snap.progress(), 0.0001);
        assertTrue(snap.title().contains("walking_sticks"));
        assertTrue(snap.title().contains("50.0%"));
        assertTrue(snap.title().contains("加速: §e1.0x"));
        assertFalse(snap.title().contains("+"));
    }

    @Test
    void build_appendsExtraProjectSuffix() {
        ResearchBossBarPresentation.Snapshot snap = ResearchBossBarPresentation.build(
                "sf:test",
                0L,
                1L,
                2,
                1.5,
                0L,
                2
        );
        assertTrue(snap.title().contains("+2 个"));
        assertEquals(0.01, snap.bossBarProgress(), 0.0001);
    }

    @Test
    void formatDuration_usesHoursMinutesAndSeconds() {
        assertEquals("1h05m", ResearchBossBarPresentation.formatDuration(3_900_000L));
        assertEquals("2m03s", ResearchBossBarPresentation.formatDuration(123_000L));
        assertEquals("9s", ResearchBossBarPresentation.formatDuration(9_000L));
    }

    @Test
    void busConstants_matchFrozenContract() {
        assertEquals("townresearch", ResearchBossBarPresentation.SOURCE);
        assertEquals(160L, ResearchBossBarPresentation.TTL_TICKS);
        assertTrue(ResearchBossBarPresentation.TTL_TICKS >= 3L * 40L,
                "TTL must cover at least three 40-tick refresh intervals");
    }

    @Test
    void key_usesTownName() {
        assertEquals("SpawnTown", ResearchBossBarPresentation.keyForTown("SpawnTown"));
    }
}
