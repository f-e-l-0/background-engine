package com.odero.engine.messaging.topology;

/** Single source of truth for every exchange, queue, and routing key name. */
public final class TopologyConstants {

    private TopologyConstants() {}

    public static final String TASKS_EXCHANGE = "tasks.exchange";
    public static final String RETRY_EXCHANGE = "tasks.retry.exchange";
    public static final String PARKING_EXCHANGE = "tasks.parking.exchange";

    public static final String MAIN_QUEUE = "tasks.main";
    public static final String PARKING_QUEUE = "tasks.parking";

    public static final String TASK_SUBMITTED_KEY = "task.submitted";
    public static final String ATTEMPT_HEADER = "x-attempt";

    public static String retryQueue(int tier) {
        return "tasks.retry.tier." + tier;
    }

    public static String retryKey(int tier) {
        return "retry.tier." + tier;
    }
}
