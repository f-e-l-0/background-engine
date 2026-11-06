package com.odero.engine.task.service;

import com.odero.engine.config.EngineProperties;
import com.odero.engine.task.domain.Task;
import com.odero.engine.task.domain.TaskStatus;
import com.odero.engine.task.domain.TaskTransitions;
import com.odero.engine.task.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class TaskStateService {

    private final TaskRepository tasks;
    private final Clock clock;
    private final Duration lease;

    public TaskStateService(TaskRepository tasks, Clock clock, EngineProperties props) {
        this.tasks = tasks;
        this.clock = clock;
        this.lease = props.taskLease();
    }

    @Transactional
    public StartOutcome startProcessing(UUID id) {
        Task task = require(id);
        Instant now = clock.instant();
        if (TaskTransitions.canStart(task.getStatus(), task.getStartedAt(), now, lease)) {
            task.startProcessing(now);
            return StartOutcome.STARTED;
        }
        boolean finished = task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.FAILED;
        return finished ? StartOutcome.FINISHED : StartOutcome.HELD;
    }

    @Transactional
    public void markCompleted(UUID id) {
        require(id).markCompleted(clock.instant());
    }

    @Transactional
    public void markRetrying(UUID id, String error) {
        require(id).markRetrying(error, clock.instant());
    }

    @Transactional
    public void markFailed(UUID id, String error) {
        require(id).markFailed(error, clock.instant());
    }

    private Task require(UUID id) {
        return tasks.lockById(id).orElseThrow(() -> new IllegalStateException("unknown task " + id));
    }
}
