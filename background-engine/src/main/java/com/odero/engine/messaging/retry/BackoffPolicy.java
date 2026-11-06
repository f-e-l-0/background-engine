package com.odero.engine.messaging.retry;

import java.time.Duration;
import java.util.List;

/**
 * Retry schedule: tier i waits tiers[i] before the message is redelivered.
 * The number of tiers is the maximum number of retries.
 */
public final class BackoffPolicy {

    private final List<Duration> tiers;

    public BackoffPolicy(List<Duration> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            throw new IllegalArgumentException("at least one retry tier is required");
        }
        for (Duration delay : tiers) {
            if (delay.isNegative() || delay.isZero()) {
                throw new IllegalArgumentException("retry delays must be positive, got " + delay);
            }
        }
        this.tiers = List.copyOf(tiers);
    }

    public int maxRetries() {
        return tiers.size();
    }

    public Duration delayFor(int tier) {
        return tiers.get(tier);
    }
}
