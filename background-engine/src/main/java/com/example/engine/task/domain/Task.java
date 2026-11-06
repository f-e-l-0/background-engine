package com.example.engine.task.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

import static com.example.engine.task.domain.TaskStatus.*;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private int attempts;

    private Instant startedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private String lastError;

    /** Optimistic lock as a second safety net beneath the pessimistic lock. */
    @Version
    private long version;

    protected Task() {
    }

    public static Task create(UUID id, String tenantId, String type, Instant now) {
        Task task = new Task();
        task.id = id;
        task.tenantId = tenantId;
        task.type = type;
        task.status = PENDING;
        task.updatedAt = now;
        return task;
    }

    public void startProcessing(Instant now) {
        transitionTo(PROCESSING, now);
        attempts++;
        startedAt = now;
    }

    public void markCompleted(Instant now) {
        transitionTo(COMPLETED, now);
    }

    public void markRetrying(String error, Instant now) {
        lastError = error;
        transitionTo(RETRYING, now);
    }

    public void markFailed(String error, Instant now) {
        lastError = error;
        transitionTo(FAILED, now);
    }

    private void transitionTo(TaskStatus next, Instant now) {
        TaskTransitions.assertAllowed(status, next);
        status = next;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getType() { return type; }
    public TaskStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastError() { return lastError; }
}
