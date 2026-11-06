package com.odero.engine.schedule.domain;

import jakarta.persistence.*;
import org.springframework.scheduling.support.CronExpression;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A recurring rule: "every <cron>, create a task of <taskType> for <tenant>".
 * Cron is evaluated in UTC. Six fields, Spring style: second minute hour day month weekday.
 */
@Entity
@Table(name = "schedules")
public class Schedule {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String taskType;

    @Column(nullable = false)
    private String cronExpression;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant nextRunAt;

    private Instant lastRunAt;

    @Version
    private long version;

    protected Schedule() {
    }

    public static Schedule create(UUID id, String tenantId, String taskType, String cronExpression, Instant now) {
        Schedule schedule = new Schedule();
        schedule.id = id;
        schedule.tenantId = tenantId;
        schedule.taskType = taskType;
        schedule.cronExpression = cronExpression;
        schedule.enabled = true;
        schedule.nextRunAt = nextAfter(cronExpression, now);
        return schedule;
    }

    /**
     * Called by the dispatcher after it has created the task. The next run is computed from NOW,
     * not from the old nextRunAt. If the engine was down for three days, it fires once, not 72 times.
     */
    public void fire(Instant now) {
        lastRunAt = now;
        nextRunAt = nextAfter(cronExpression, now);
    }

    /** Throws IllegalArgumentException for a malformed cron, so bad input never reaches the database. */
    static Instant nextAfter(String cron, Instant instant) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime next = expression.next(instant.atZone(ZoneOffset.UTC));
        if (next == null) {
            throw new IllegalArgumentException("cron never fires: " + cron);
        }
        return next.toInstant();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTaskType() { return taskType; }
    public String getCronExpression() { return cronExpression; }
    public boolean isEnabled() { return enabled; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastRunAt() { return lastRunAt; }
}
