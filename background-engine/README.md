# Background Processing Engine

Spring Boot 3.3 / Java 21 engine: RabbitMQ quorum queues, TTL-based retry tiers, a parking lot,
Redis idempotency claims, Postgres as the source of truth, and cron-style schedules.

## Roles
One jar, three roles, switched by properties:
- API: always on. Accepts tasks and schedules over HTTP.
- Worker: `engine.worker.enabled` (default true). Consumes `tasks.main`.
- Scheduler: `engine.scheduler.enabled` (default true). Turns due schedules into tasks.

Run them as separate deployments by disabling the roles you don't need on each.

## Run locally
    docker compose up --build

    # one-off task
    curl -X POST localhost:8080/api/tasks -H 'Content-Type: application/json' \
         -d '{"tenantId":"acme","type":"demo.echo"}'

    # recurring task: every hour, on the hour (6-field cron, UTC)
    curl -X POST localhost:8080/api/schedules -H 'Content-Type: application/json' \
         -d '{"tenantId":"acme","taskType":"demo.echo","cron":"0 0 * * * *"}'

Admin UI: http://localhost:15672 (engine / engine)

## Test
    mvn test

## Not yet built
Transactional outbox, SLA watchdog, full metrics set, tenant and W3C trace propagation,
parking-lot replay API, schedule pause/delete endpoints, per-schedule failure isolation,
Kubernetes manifests, 3-node cluster config, migration and blue-green runbooks.
