package com.example.engine.messaging.topology;

import com.example.engine.messaging.retry.BackoffPolicy;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

import static com.example.engine.messaging.topology.TopologyConstants.*;

/**
 * Flow of a message:
 *
 *   producer --> tasks.exchange --(task.#)--> tasks.main --(rejected, no requeue)--> parking
 *                                                 |
 *                                   handler fails, retries left
 *                                                 v
 *                 tasks.retry.exchange --(retry.tier.N)--> tasks.retry.tier.N
 *                                                 |  (TTL expires = backoff elapsed)
 *                                                 v
 *                 dead-letter back to tasks.exchange with task.submitted --> tasks.main
 *
 * Backoff costs no worker time: waiting messages sit in a queue, not in a sleeping thread.
 */
@Configuration
public class RabbitTopology {

    @Bean
    TopicExchange tasksExchange() {
        return new TopicExchange(TASKS_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE, true, false);
    }

    @Bean
    FanoutExchange parkingExchange() {
        return new FanoutExchange(PARKING_EXCHANGE, true, false);
    }

    @Bean
    Queue mainQueue() {
        return QueueBuilder.durable(MAIN_QUEUE)
                .quorum()
                .deadLetterExchange(PARKING_EXCHANGE)
                // Poison-message guard: after 20 redeliveries the broker parks the message.
                .withArgument("x-delivery-limit", 20)
                .build();
    }

    @Bean
    Binding mainBinding(Queue mainQueue, TopicExchange tasksExchange) {
        return BindingBuilder.bind(mainQueue).to(tasksExchange).with("task.#");
    }

    @Bean
    Queue parkingQueue() {
        return QueueBuilder.durable(PARKING_QUEUE).quorum().build();
    }

    @Bean
    Binding parkingBinding(Queue parkingQueue, FanoutExchange parkingExchange) {
        return BindingBuilder.bind(parkingQueue).to(parkingExchange);
    }

    /** One TTL queue per retry tier. Per-queue TTL is used because quorum queues do not support per-message TTL. */
    @Bean
    Declarables retryTopology(BackoffPolicy policy, DirectExchange retryExchange) {
        List<Declarable> declarables = new ArrayList<>();
        for (int tier = 0; tier < policy.maxRetries(); tier++) {
            Queue retryQueue = QueueBuilder.durable(retryQueue(tier))
                    .quorum()
                    .ttl((int) policy.delayFor(tier).toMillis())
                    .deadLetterExchange(TASKS_EXCHANGE)
                    .deadLetterRoutingKey(TASK_SUBMITTED_KEY)
                    .build();
            declarables.add(retryQueue);
            declarables.add(BindingBuilder.bind(retryQueue).to(retryExchange).with(retryKey(tier)));
        }
        return new Declarables(declarables);
    }
}
