CREATE TABLE tasks (
    id          UUID PRIMARY KEY,
    tenant_id   VARCHAR(64)  NOT NULL,
    type        VARCHAR(128) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    started_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ  NOT NULL,
    last_error  TEXT,
    version     BIGINT       NOT NULL DEFAULT 0
);

-- Supports the SLA watchdog query: tasks stuck in PROCESSING since time X.
CREATE INDEX idx_tasks_status_started ON tasks (status, started_at);
