package com.xinantown.townresearch.model;

import com.xinantown.townresearch.ResearchSettings;

import java.util.*;

/**
 * Manages a single town's research state: labs, active projects, and completed techs.
 */
public class TownResearch {

    private final String townName;
    private final int maxLabs;
    private final Set<ResearchLab> labs = new LinkedHashSet<>();
    private final Set<String> completed = new LinkedHashSet<>();
    private final Map<ResearchLab, ResearchProject> activeProjects = new LinkedHashMap<>();
    private final Map<ResearchLab, ResearchProject> pausedProjects = new LinkedHashMap<>();
    private final Set<UUID> researchers = new LinkedHashSet<>();
    private final Set<UUID> pendingRevokes = new LinkedHashSet<>();
    private int paidSpeedLevel = 0;

    public TownResearch(String townName, int maxLabs) {
        this.townName = townName;
        this.maxLabs = maxLabs;
    }

    // === Labs ===

    public Set<ResearchLab> getLabs() { return Collections.unmodifiableSet(labs); }

    public boolean canAddLab() { return labs.size() < maxLabs; }

    public void addLab(ResearchLab lab) {
        if (!canAddLab()) throw new IllegalStateException("Max labs (" + maxLabs + ") reached for " + townName);
        labs.add(lab);
        // Resume paused project if this lab was previously paused
        ResearchProject resumed = pausedProjects.remove(lab);
        if (resumed != null) {
            long elapsed = System.currentTimeMillis() - resumed.startedAt();
            long remainingMs = resumed.durationMinutes() * 60000 - elapsed;
            long remainingMin = Math.max(1, remainingMs / 60000);
            activeProjects.put(lab, new ResearchProject(resumed.sfKey(), System.currentTimeMillis(), remainingMin));
        }
    }

    public void removeLab(ResearchLab lab) {
        labs.remove(lab);
        // Pause instead of discarding
        ResearchProject paused = activeProjects.remove(lab);
        if (paused != null) pausedProjects.put(lab, paused);
    }

    // === Projects ===

    public Map<ResearchLab, ResearchProject> getActiveProjects() { return Collections.unmodifiableMap(activeProjects); }

    public void pauseProject(ResearchLab lab) {
        ResearchProject p = activeProjects.remove(lab);
        if (p != null) pausedProjects.put(lab, p);
    }

    public void resumeProject(ResearchLab lab) {
        ResearchProject p = pausedProjects.remove(lab);
        if (p != null) {
            long elapsed = System.currentTimeMillis() - p.startedAt();
            long remainingMs = Math.max(0, p.durationMinutes() * 60000 - elapsed);
            long remainingMin = Math.max(1, remainingMs / 60000);
            activeProjects.put(lab, new ResearchProject(p.sfKey(), System.currentTimeMillis(), remainingMin));
        }
    }

    public boolean isPaused(ResearchLab lab) { return pausedProjects.containsKey(lab); }

    public Map<ResearchLab, ResearchProject> getPausedProjects() { return Collections.unmodifiableMap(pausedProjects); }

    public void startProject(ResearchLab lab, String sfKey, long durationMinutes) {
        if (!labs.contains(lab)) throw new IllegalArgumentException("Lab not registered for " + townName);
        if (activeProjects.containsKey(lab)) throw new IllegalStateException("Lab already has an active project");
        activeProjects.put(lab, new ResearchProject(sfKey, System.currentTimeMillis(), durationMinutes));
    }

    /** Load a project from persistence with its original startedAt timestamp. */
    public void resumeProject(ResearchLab lab, String sfKey, long startedAt, long durationMinutes) {
        if (!labs.contains(lab)) return;
        activeProjects.put(lab, new ResearchProject(sfKey, startedAt, durationMinutes));
    }

    public void cancelProject(ResearchLab lab) { activeProjects.remove(lab); }

    public boolean isResearching(String sfKey) {
        return activeProjects.values().stream().anyMatch(p -> p.sfKey().equals(sfKey));
    }

    /**
     * Count how many labs are researching the given tech (for acceleration).
     */
    public int countLabsResearching(String sfKey) {
        return (int) activeProjects.entrySet().stream()
                .filter(e -> e.getValue().sfKey().equals(sfKey))
                .count();
    }

    /**
     * Calculate effective duration with multi-lab acceleration and paid speed.
     * Formula: baseMinutes × researchMultiplier ÷ labSpeedMultiplier ÷ paidSpeedMultiplier
     *
     * @param sfKey           the research key
     * @param baseDurationMinutes the base research time in minutes
     * @param settings        the research settings (config)
     * @return effective duration in minutes (minimum 1)
     */
    public long getEffectiveDuration(String sfKey, long baseDurationMinutes, ResearchSettings settings) {
        double multiplier = 1.0;
        double labSpeed = 1.0;
        double paidMultiplier = 1.0;

        if (settings != null) {
            multiplier = settings.getResearchMultiplier(sfKey);
            int n = countLabsResearching(sfKey);
            labSpeed = settings.calcLabSpeedMultiplier(n);
            paidMultiplier = settings.getPaidSpeedLevelMultiplier(paidSpeedLevel);
        } else {
            // Fallback when no settings available (e.g. unit tests)
            int n = countLabsResearching(sfKey);
            if (n > 1) labSpeed = n;
        }

        double effective = baseDurationMinutes * multiplier / labSpeed / paidMultiplier;
        return Math.max(1, (long) Math.ceil(effective));
    }

    /**
     * @deprecated Use {@link #getEffectiveDuration(String, long, ResearchSettings)} instead.
     */
    @Deprecated
    public long getEffectiveDuration(String sfKey, long baseDurationMinutes) {
        return getEffectiveDuration(sfKey, baseDurationMinutes, null);
    }

    // === Completed ===

    public boolean isCompleted(String sfKey) { return completed.contains(sfKey); }

    public void addCompleted(String sfKey) {
        completed.add(sfKey);
        // Cancel all active projects for this key
        activeProjects.entrySet().removeIf(e -> e.getValue().sfKey().equals(sfKey));
    }

    public Set<String> getCompleted() { return Collections.unmodifiableSet(completed); }

    // === Researchers ===

    public Set<UUID> getResearchers() { return Collections.unmodifiableSet(researchers); }

    public void addResearcher(UUID uuid) { researchers.add(uuid); }

    public void removeResearcher(UUID uuid) { researchers.remove(uuid); }

    public Set<UUID> getPendingRevokes() { return Collections.unmodifiableSet(pendingRevokes); }
    public void addPendingRevoke(UUID uuid) { pendingRevokes.add(uuid); }
    public void removePendingRevoke(UUID uuid) { pendingRevokes.remove(uuid); }

    // === Paid Speed ===

    public int getPaidSpeedLevel() { return paidSpeedLevel; }

    public void setPaidSpeedLevel(int level) {
        this.paidSpeedLevel = Math.max(0, level);
    }

    // === Convenience ===

    /** Find the first lab with no active project. */
    public ResearchLab findFreeLab() {
        return labs.stream()
                .filter(l -> !activeProjects.containsKey(l))
                .findFirst().orElse(null);
    }

    /**
     * Check all active projects for completion. Returns list of newly completed sfKeys.
     * Uses effective duration for multi-lab acceleration and paid speed.
     */
    public List<String> checkCompletions(long nowMs, ResearchSettings settings) {
        List<String> newlyCompleted = new ArrayList<>();
        var iter = activeProjects.entrySet().iterator();
        while (iter.hasNext()) {
            var e = iter.next();
            ResearchProject p = e.getValue();
            long effectiveMs = getEffectiveDuration(p.sfKey(), p.durationMinutes(), settings) * 60000;
            if (nowMs - p.startedAt() >= effectiveMs) {
                newlyCompleted.add(p.sfKey());
                iter.remove();
            }
        }
        // Add to completed AFTER iteration to avoid ConcurrentModificationException
        for (String key : newlyCompleted) {
            completed.add(key);
            // Remove any remaining active projects for this key
            activeProjects.entrySet().removeIf(e -> e.getValue().sfKey().equals(key));
        }
        return newlyCompleted;
    }

    /**
     * @deprecated Use {@link #checkCompletions(long, ResearchSettings)} instead.
     */
    @Deprecated
    public List<String> checkCompletions(long nowMs) {
        return checkCompletions(nowMs, null);
    }
}
