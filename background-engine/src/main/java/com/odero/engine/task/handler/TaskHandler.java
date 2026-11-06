package com.odero.engine.task.handler;

import com.odero.engine.messaging.TaskMessage;

/**
 * Extension point. Add a class implementing this interface and the engine picks it up.
 * Throw PermanentFailureException for bad input that retrying cannot fix.
 * Throw any other RuntimeException for transient failures, which will be retried.
 */
public interface TaskHandler {

    String type();

    void handle(TaskMessage message);
}
