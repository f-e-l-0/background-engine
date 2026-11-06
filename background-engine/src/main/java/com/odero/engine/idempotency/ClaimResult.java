package com.odero.engine.idempotency;

public enum ClaimResult {
    /** We hold the lock and must do the work. */
    CLAIMED,
    /** Someone already finished this task. Acknowledge and move on. */
    ALREADY_DONE,
    /** Someone else is working on it right now. Retry later. Do not drop. */
    IN_FLIGHT
}
