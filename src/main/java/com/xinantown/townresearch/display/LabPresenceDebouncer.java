package com.xinantown.townresearch.display;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Debounces lab enter/leave so PlayerMoveEvent jitter does not flap BossBar visibility.
 * Enter shows immediately; leave waits {@code graceTicks} before HIDE.
 */
public final class LabPresenceDebouncer {

    public enum Decision {
        SHOW,
        KEEP,
        HIDE
    }

    private final long graceTicks;
    private final Map<UUID, State> states = new HashMap<>();

    public LabPresenceDebouncer(long graceTicks) {
        this.graceTicks = Math.max(0L, graceTicks);
    }

    public Decision onPresenceChanged(UUID playerId, boolean nearLab, long nowTick) {
        Objects.requireNonNull(playerId, "playerId");
        State state = states.get(playerId);

        if (nearLab) {
            if (state == null) {
                states.put(playerId, new State(true, -1L));
                return Decision.SHOW;
            }
            boolean wasShowing = state.showing;
            state.showing = true;
            state.hideAtTick = -1L;
            return wasShowing ? Decision.KEEP : Decision.SHOW;
        }

        // outside lab
        if (state == null || !state.showing) {
            return Decision.KEEP;
        }
        if (graceTicks <= 0L) {
            states.remove(playerId);
            return Decision.HIDE;
        }
        if (state.hideAtTick < 0L) {
            state.hideAtTick = nowTick + graceTicks;
        }
        return Decision.KEEP;
    }

    public Decision tick(UUID playerId, long nowTick) {
        Objects.requireNonNull(playerId, "playerId");
        State state = states.get(playerId);
        if (state == null || !state.showing || state.hideAtTick < 0L) {
            return Decision.KEEP;
        }
        if (nowTick >= state.hideAtTick) {
            states.remove(playerId);
            return Decision.HIDE;
        }
        return Decision.KEEP;
    }

    public boolean shouldDisplay(UUID playerId, long nowTick) {
        Objects.requireNonNull(playerId, "playerId");
        State state = states.get(playerId);
        if (state == null || !state.showing) {
            return false;
        }
        if (state.hideAtTick >= 0L && nowTick >= state.hideAtTick) {
            states.remove(playerId);
            return false;
        }
        return true;
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            states.remove(playerId);
        }
    }

    public void clearAll() {
        states.clear();
    }

    private static final class State {
        boolean showing;
        long hideAtTick;

        State(boolean showing, long hideAtTick) {
            this.showing = showing;
            this.hideAtTick = hideAtTick;
        }
    }
}
