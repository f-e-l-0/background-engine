package com.odero.engine.task.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** The one place that defines which status changes are legal. */
public final class TaskTransitions {

    private static final Map<TaskStatus, Set<TaskStatus>> LEGAL = new EnumMap<>(TaskStatus.class);

    static {
        LEGAL.put(TaskStatus.PENDING, EnumSet.of(TaskStatus.PROCESSING));
        LEGAL.put(TaskStatus.RETRYING, EnumSet.of(TaskStatus.PROCESSING));
        // PROCESSING -> PROCESSING is a lease takeover after a worker died.
        LEGAL.put(TaskStatus.PROCESSING, EnumSet.of(
                TaskStatus.PROCESSING, TaskStatus.COMPLETED, TaskStatus.RETRYING, TaskStatus.FAILED));
        LEGAL.put(TaskStatus.COMPLETED, EnumSet.noneOf(TaskStatus.class));
        // FAILED -> PENDING is the operator replay path from the parking lot.
        LEGAL.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.PENDING));
    }

    private TaskTransitions() {}

    public static void assertAllowed(TaskStatus from, TaskStatus to) {
        if (!LEGAL.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to)) {
            throw new IllegalStateException("Illegal task transition " + from + " -> " + to);
        }
    }

    /** A task can start if it is waiting, or if it is PROCESSING but its lease has expired. */
    public static boolean canStart(TaskStatus status, Instant startedAt, Instant now, Duration lease) {
        return switch (status) {
            case PENDING, RETRYING -> true;
            case PROCESSING -> startedAt == null || startedAt.plus(lease).isBefore(now);
            case COMPLETED, FAILED -> false;
        };
    }
}
