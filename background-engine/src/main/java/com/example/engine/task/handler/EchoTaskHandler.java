package com.example.engine.task.handler;

import com.example.engine.messaging.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Reference implementation of the SPI. Replace or copy it when adding real workloads. */
@Component
public class EchoTaskHandler implements TaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EchoTaskHandler.class);

    @Override
    public String type() {
        return "demo.echo";
    }

    @Override
    public void handle(TaskMessage message) {
        log.info("handled task {} for tenant {}", message.taskId(), message.tenantId());
    }
}
