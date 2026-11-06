package com.odero.engine.messaging.consume;

import com.odero.engine.common.exception.PermanentFailureException;
import com.odero.engine.idempotency.ClaimResult;
import com.odero.engine.idempotency.IdempotencyGuard;
import com.odero.engine.idempotency.IdempotencyKey;
import com.odero.engine.messaging.TaskMessage;
import com.odero.engine.messaging.retry.RetryRouter;
import com.odero.engine.task.handler.TaskHandlerRegistry;
import com.odero.engine.task.service.StartOutcome;
import com.odero.engine.task.service.TaskStateService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import static com.odero.engine.messaging.topology.TopologyConstants.ATTEMPT_HEADER;
import static com.odero.engine.messaging.topology.TopologyConstants.MAIN_QUEUE;

/**
 * Three kinds of failure, three responses:
 *   1. Infrastructure (Redis or Postgres unreachable): nothing ran, so defer without spending a retry.
 *   2. Handler threw a transient error: spend one retry, wait for the tier delay.
 *   3. Handler threw PermanentFailureException, or retries are exhausted: park for a human.
 */
@Component
@ConditionalOnProperty(prefix = "engine.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskMessageListener {

    private static final Logger log = LoggerFactory.getLogger(TaskMessageListener.class);

    private final IdempotencyGuard idempotency;
    private final TaskStateService state;
    private final TaskHandlerRegistry handlers;
    private final RetryRouter retries;
    private final MeterRegistry metrics;

    public TaskMessageListener(IdempotencyGuard idempotency, TaskStateService state,
                               TaskHandlerRegistry handlers, RetryRouter retries, MeterRegistry metrics) {
        this.idempotency = idempotency;
        this.state = state;
        this.handlers = handlers;
        this.retries = retries;
        this.metrics = metrics;
    }

    @RabbitListener(queues = MAIN_QUEUE, containerFactory = "taskListenerContainerFactory")
    public void onTask(TaskMessage message,
                       @Header(name = ATTEMPT_HEADER, defaultValue = "0") int retriesDone) {
        IdempotencyKey key = IdempotencyKey.of(message.tenantId(), message.taskId());
        if (acquire(message, key, retriesDone)) {
            runHandler(message, key, retriesDone);
        }
    }

    /** Returns true only when this worker owns the task and must run it. */
    private boolean acquire(TaskMessage message, IdempotencyKey key, int retriesDone) {
        try {
            ClaimResult claim = idempotency.claim(key);
            if (claim == ClaimResult.ALREADY_DONE) {
                count("duplicate");
                return false;
            }
            if (claim == ClaimResult.IN_FLIGHT) {
                retries.defer(message, retriesDone);
                count("deferred");
                return false;
            }

            StartOutcome start = state.startProcessing(message.taskId());
            switch (start) {
                case STARTED -> {
                    return true;
                }
                case FINISHED -> {
                    idempotency.markDone(key);
                    count("duplicate");
                    return false;
                }
                case HELD -> {
                    // A live lease belongs to another worker. Dropping the message here would lose the task.
                    idempotency.release(key);
                    retries.defer(message, retriesDone);
                    count("deferred");
                    return false;
                }
            }
            return false;
        } catch (RuntimeException infrastructure) {
            log.warn("infrastructure failure while acquiring task {}, deferring", message.taskId(), infrastructure);
            safeRelease(key);
            retries.defer(message, retriesDone);
            return false;
        }
    }

    private void runHandler(TaskMessage message, IdempotencyKey key, int retriesDone) {
        try {
            handlers.require(message.type()).handle(message);
        } catch (PermanentFailureException ex) {
            idempotency.release(key);
            state.markFailed(message.taskId(), ex.getMessage());
            count("parked");
            throw new AmqpRejectAndDontRequeueException("permanent failure: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            idempotency.release(key);
            onTransientFailure(message, retriesDone, ex);
            return;
        }

        // Handler finished. If these writes fail, the exception parks the message for operator replay.
        state.markCompleted(message.taskId());
        idempotency.markDone(key);
        count("completed");
    }

    private void onTransientFailure(TaskMessage message, int retriesDone, RuntimeException ex) {
        if (!retries.hasBudget(retriesDone)) {
            log.error("task {} exhausted retries, parking", message.taskId(), ex);
            state.markFailed(message.taskId(), ex.getMessage());
            count("parked");
            throw new AmqpRejectAndDontRequeueException("retries exhausted for " + message.taskId(), ex);
        }
        // Publish first, then record RETRYING. If the database write fails, the row stays PROCESSING
        // and its lease expires, so the retry is still eligible. The reverse order could leave a
        // RETRYING row with no message behind it.
        retries.scheduleRetry(message, retriesDone);
        count("retried");
        try {
            state.markRetrying(message.taskId(), ex.getMessage());
        } catch (RuntimeException dbDown) {
            log.warn("could not record RETRYING for task {}; lease will make it eligible", message.taskId(), dbDown);
        }
    }

    private void safeRelease(IdempotencyKey key) {
        try {
            idempotency.release(key);
        } catch (RuntimeException ignored) {
            // The claim TTL expires on its own. Nothing else to do here.
        }
    }

    private void count(String outcome) {
        metrics.counter("engine.task.outcomes", "outcome", outcome).increment();
    }
}
