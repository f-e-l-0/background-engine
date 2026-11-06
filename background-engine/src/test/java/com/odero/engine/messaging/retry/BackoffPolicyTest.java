package com.odero.engine.messaging.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackoffPolicyTest {

    @Test
    void exposesRetryCountAndTierDelays() {
        BackoffPolicy policy = new BackoffPolicy(List.of(Duration.ofSeconds(1), Duration.ofSeconds(5)));

        assertThat(policy.maxRetries()).isEqualTo(2);
        assertThat(policy.delayFor(1)).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void rejectsEmptySchedule() {
        assertThatThrownBy(() -> new BackoffPolicy(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroOrNegativeDelay() {
        assertThatThrownBy(() -> new BackoffPolicy(List.of(Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
