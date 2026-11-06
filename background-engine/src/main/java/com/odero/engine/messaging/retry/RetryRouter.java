package com.odero.engine.messaging.retry;

import com.odero.engine.messaging.TaskMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import static com.odero.engine.messaging.topology.TopologyConstants.*;

@Component
public class RetryRouter {

    private final RabbitTemplate rabbit;
    private final BackoffPolicy policy;

    public RetryRouter(RabbitTemplate rabbit, BackoffPolicy policy) {
        this.rabbit = rabbit;
        this.policy = policy;
    }

    public boolean hasBudget(int retriesDone) {
        return retriesDone < policy.maxRetries();
    }

    /** Failed attempt: wait the delay for this tier, then retry with the counter incremented. */
    public void scheduleRetry(TaskMessage message, int retriesDone) {
        publish(message, retriesDone, retriesDone + 1);
    }

    /** Lock contention: try again soon without spending a retry. */
    public void defer(TaskMessage message, int retriesDone) {
        publish(message, 0, retriesDone);
    }

    private void publish(TaskMessage message, int tier, int attemptHeader) {
        rabbit.convertAndSend(RETRY_EXCHANGE, retryKey(tier), message, outgoing -> {
            outgoing.getMessageProperties().setHeader(ATTEMPT_HEADER, attemptHeader);
            return outgoing;
        });
    }
}
