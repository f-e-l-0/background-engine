package com.odero.engine.idempotency;

import java.util.UUID;

public record IdempotencyKey(String value) {

    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
    }

    /** Tenant is part of the key so identical task IDs in different tenants never collide. */
    public static IdempotencyKey of(String tenantId, UUID taskId) {
        return new IdempotencyKey(tenantId + ":" + taskId);
    }
}
