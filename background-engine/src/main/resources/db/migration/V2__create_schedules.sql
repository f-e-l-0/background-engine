CREATE TABLE schedules (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    task_type       VARCHAR(128) NOT NULL,
    cron_expression VARCHAR(128) NOT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    next_run_at     TIMESTAMPTZ  NOT NULL,
    last_run_at     TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0
);

-- The dispatcher's poll query filters on (enabled, next_run_at).
CREATE INDEX idx_schedules_due ON schedules (enabled, next_run_at);
