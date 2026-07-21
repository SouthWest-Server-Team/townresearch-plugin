package com.xinantown.townresearch.model;

/**
 * A research project running on a specific lab.
 */
public record ResearchProject(String sfKey, long startedAt, long durationMinutes) {}
