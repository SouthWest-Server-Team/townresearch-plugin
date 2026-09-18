package com.xinantown.townresearch.display;

/**
 * Pure presentation helpers for research BossBar title/progress.
 * Keeps formatting and display policy free of Bukkit coupling.
 */
public final class ResearchBossBarPresentation {

    public static final String SOURCE = "townresearch";
    /** Refresh timer is 40 ticks; TTL covers one missed refresh + margin. */
    public static final long TTL_TICKS = 100L;
    public static final long LEAVE_GRACE_TICKS = 40L;

    private ResearchBossBarPresentation() {
    }

    public static boolean shouldDisplay(boolean hasActiveProjects, boolean viewerPresent) {
        return hasActiveProjects && viewerPresent;
    }

    public static String keyForTown(String townName) {
        return townName == null ? "" : townName;
    }

    public static Snapshot build(
            String sfKey,
            long startedAtMs,
            long durationMinutes,
            int labCount,
            double speedMultiplier,
            long nowMs,
            int extraProjectCount
    ) {
        long effectiveMs = Math.max(1L, durationMinutes) * 60_000L;
        // durationMinutes here is already the effective duration from TownResearch.
        long elapsed = Math.max(0L, nowMs - startedAtMs);
        double progress = Math.min(1.0, (double) elapsed / effectiveMs);
        String displayKey = displayKey(sfKey);
        String remainingStr = formatDuration(Math.max(0L, effectiveMs - elapsed));
        String suffix = extraProjectCount > 0
                ? " §7| §8+" + extraProjectCount + " 个"
                : "";
        String title = "§b§l⚡ 研究进度 "
                + "§e" + displayKey
                + " §7| §a" + String.format("%.1f", progress * 100) + "%"
                + " §7| ⏱ " + remainingStr
                + " §7| 加速: §e" + String.format("%.1f", speedMultiplier) + "x"
                + suffix;
        return new Snapshot(displayKey, title, progress, Math.max(0.01, progress));
    }

    public static String displayKey(String sfKey) {
        if (sfKey == null) {
            return "";
        }
        int idx = sfKey.indexOf(':');
        return idx >= 0 ? sfKey.substring(idx + 1) : sfKey;
    }

    public static String formatDuration(long remainingMs) {
        long totalSec = remainingMs / 1000;
        if (totalSec >= 3600) {
            return String.format("%dh%02dm", totalSec / 3600, (totalSec % 3600) / 60);
        }
        if (totalSec >= 60) {
            return String.format("%dm%02ds", totalSec / 60, totalSec % 60);
        }
        return totalSec + "s";
    }

    public record Snapshot(String displayKey, String title, double progress, double bossBarProgress) {
    }
}
