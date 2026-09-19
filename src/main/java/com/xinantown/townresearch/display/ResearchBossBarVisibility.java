package com.xinantown.townresearch.display;

import com.xinantown.townresearch.model.ResearchLab;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure plot-based visibility rules for research BossBar.
 * Standing on a lab TownBlock is enough — town residency is not required.
 */
public final class ResearchBossBarVisibility {

    private ResearchBossBarVisibility() {
    }

    public record OnlinePresence(UUID playerId, boolean nearLab) {
    }

    public static boolean matchesAnyLab(
            String worldName,
            int townBlockX,
            int townBlockZ,
            Iterable<ResearchLab> labs
    ) {
        if (worldName == null || labs == null) {
            return false;
        }
        for (ResearchLab lab : labs) {
            if (lab == null) {
                continue;
            }
            if (worldName.equals(lab.worldName())
                    && townBlockX == lab.townBlockX()
                    && townBlockZ == lab.townBlockZ()) {
                return true;
            }
        }
        return false;
    }

    public static boolean includeOnlineViewer(boolean online, boolean nearLab) {
        return online && nearLab;
    }

    /**
     * Resolve which town's research BossBar should bind for a location.
     * Uses the TownBlock's town (plot town), not the player's membership town.
     */
    public static Optional<String> resolveActiveTownForLocation(
            String playerMembershipTown,
            String plotTownName,
            Predicate<String> hasActiveResearch
    ) {
        if (plotTownName == null || plotTownName.isBlank()) {
            return Optional.empty();
        }
        if (hasActiveResearch == null || !hasActiveResearch.test(plotTownName)) {
            return Optional.empty();
        }
        return Optional.of(plotTownName);
    }

    public static Set<UUID> selectNearLabPlayerIds(Iterable<OnlinePresence> onlinePresences) {
        Set<UUID> selected = new HashSet<>();
        if (onlinePresences == null) {
            return selected;
        }
        for (OnlinePresence presence : onlinePresences) {
            if (presence == null || presence.playerId() == null) {
                continue;
            }
            if (includeOnlineViewer(true, presence.nearLab())) {
                selected.add(presence.playerId());
            }
        }
        return selected;
    }

    public static boolean debugLoggingEnabled(boolean configured) {
        return configured;
    }
}
