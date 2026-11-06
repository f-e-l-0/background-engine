package com.odero.engine.messaging.publish;

import com.odero.engine.messaging.TaskMessage;
import com.odero.engine.task.domain.Task;
import com.odero.engine.task.repository.TaskRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

import static com.odero.engine.messaging.topology.TopologyConstants.TASKS_EXCHANGE;
import static com.odero.engine.messaging.topology.TopologyConstants.TASK_SUBMITTED_KEY;

@Service
public class TaskSubmissionService {

    private final TaskRepository tasks;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    public TaskSubmissionService(TaskRepository tasks, RabbitTemplate rabbit, Clock clock) {
        this.tasks = tasks;
        this.rabbit = rabbit;
        this.clock = clock;
    }

    /**
     * Saves the task row, then publishes ONLY after the database commit succeeds.
     * Publishing before commit could make a worker run a task that does not exist yet.
     * Known gap: if the publish fails after commit, the row stays PENDING. The transactional
     * outbox pattern closes that gap and is the next step.
     */
    @Transactional
    public UUID submit(String tenantId, String type) {
        UUID id = UUID.randomUUID();
        tasks.save(Task.create(id, tenantId, type, clock.instant()));

        TaskMessage message = new TaskMessage(id, tenantId, type);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbit.convertAndSend(TASKS_EXCHANGE, TASK_SUBMITTED_KEY, message);
            }
        });
        return id;
    }
}
