package com.xinantown.townresearch.display;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LabPresenceDebouncerTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void enterLab_showsImmediately() {
        LabPresenceDebouncer debouncer = new LabPresenceDebouncer(40L);
        assertEquals(LabPresenceDebouncer.Decision.SHOW,
                debouncer.onPresenceChanged(PLAYER, true, 100L));
        assertTrue(debouncer.shouldDisplay(PLAYER, 100L));
    }

    @Test
    void leaveLab_keepsShowingUntilGraceExpires() {
        LabPresenceDebouncer debouncer = new LabPresenceDebouncer(40L);
        debouncer.onPresenceChanged(PLAYER, true, 100L);

        assertEquals(LabPresenceDebouncer.Decision.KEEP,
                debouncer.onPresenceChanged(PLAYER, false, 110L));
        assertTrue(debouncer.shouldDisplay(PLAYER, 110L));
        assertTrue(debouncer.shouldDisplay(PLAYER, 149L));

        assertEquals(LabPresenceDebouncer.Decision.HIDE,
                debouncer.tick(PLAYER, 150L));
        assertFalse(debouncer.shouldDisplay(PLAYER, 150L));
    }

    @Test
    void leaveThenReenterWithinGrace_cancelsPendingHide() {
        LabPresenceDebouncer debouncer = new LabPresenceDebouncer(40L);
        debouncer.onPresenceChanged(PLAYER, true, 100L);
        debouncer.onPresenceChanged(PLAYER, false, 110L);

        assertEquals(LabPresenceDebouncer.Decision.KEEP,
                debouncer.onPresenceChanged(PLAYER, true, 120L));
        assertTrue(debouncer.shouldDisplay(PLAYER, 160L));
        assertEquals(LabPresenceDebouncer.Decision.KEEP, debouncer.tick(PLAYER, 160L));
    }

    @Test
    void alreadyOutside_doesNotEmitHideAgain() {
        LabPresenceDebouncer debouncer = new LabPresenceDebouncer(40L);
        assertEquals(LabPresenceDebouncer.Decision.KEEP,
                debouncer.onPresenceChanged(PLAYER, false, 10L));
        assertFalse(debouncer.shouldDisplay(PLAYER, 10L));
        assertEquals(LabPresenceDebouncer.Decision.KEEP, debouncer.tick(PLAYER, 100L));
    }

    @Test
    void clear_removesPlayerState() {
        LabPresenceDebouncer debouncer = new LabPresenceDebouncer(40L);
        debouncer.onPresenceChanged(PLAYER, true, 100L);
        debouncer.clear(PLAYER);
        assertFalse(debouncer.shouldDisplay(PLAYER, 100L));
    }
}
