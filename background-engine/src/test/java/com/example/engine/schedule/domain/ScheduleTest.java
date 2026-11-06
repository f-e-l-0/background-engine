package com.example.engine.schedule.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleTest {

    @Test
    void firstRunIsTheNextCronSlot() {
        Schedule schedule = Schedule.create(UUID.randomUUID(), "acme", "report.generate",
                "0 0 * * * *", Instant.parse("2026-11-06T10:30:00Z"));

        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-11-06T11:00:00Z"));
    }

    @Test
    void downtimeDoesNotCreateABacklog() {
        Schedule schedule = Schedule.create(UUID.randomUUID(), "acme", "report.generate",
                "0 0 * * * *", Instant.parse("2026-11-06T10:30:00Z"));

        // Engine was down; it wakes at 14:20 with nextRunAt still 11:00.
        schedule.fire(Instant.parse("2026-11-06T14:20:00Z"));

        assertThat(schedule.getLastRunAt()).isEqualTo(Instant.parse("2026-11-06T14:20:00Z"));
        assertThat(schedule.getNextRunAt()).isEqualTo(Instant.parse("2026-11-06T15:00:00Z"));
    }

    @Test
    void rejectsMalformedCron() {
        assertThatThrownBy(() -> Schedule.create(UUID.randomUUID(), "acme", "report.generate",
                "not a cron", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
