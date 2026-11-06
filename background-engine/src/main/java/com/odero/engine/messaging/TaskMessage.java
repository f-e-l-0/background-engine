package com.odero.engine.messaging;

import java.util.UUID;

/** Wire contract. Keep it small: the database holds the state, the message only points at it. */
public record TaskMessage(UUID taskId, String tenantId, String type) {}
