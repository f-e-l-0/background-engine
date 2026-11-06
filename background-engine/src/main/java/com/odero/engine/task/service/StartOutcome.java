package com.odero.engine.task.service;

public enum StartOutcome {
    /** Row moved to PROCESSING. This worker must run the task. */
    STARTED,
    /** Already COMPLETED or FAILED. Acknowledge and stop. */
    FINISHED,
    /** Another worker holds a live lease. Come back later. */
    HELD
}
