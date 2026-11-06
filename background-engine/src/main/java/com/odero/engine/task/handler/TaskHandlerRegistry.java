package com.odero.engine.task.handler;

import com.odero.engine.common.exception.PermanentFailureException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> byType;

    /** toUnmodifiableMap throws on duplicate types, so a clash fails at startup, not at runtime. */
    public TaskHandlerRegistry(List<TaskHandler> handlers) {
        this.byType = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(TaskHandler::type, Function.identity()));
    }

    public TaskHandler require(String type) {
        TaskHandler handler = byType.get(type);
        if (handler == null) {
            throw new PermanentFailureException("no handler registered for task type '" + type + "'");
        }
        return handler;
    }
}
