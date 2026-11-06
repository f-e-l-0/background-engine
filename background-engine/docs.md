# Project Guide: Background Processing Engine

This guide walks you through the whole project, from the outside in. It tells you what each part
does, why it exists, and where to read next. For exact settings and API details, see
`DOCUMENTATION.md`. This file is for understanding the code.

---

## 1. The problem this project solves

Some work takes too long to run inside an HTTP request: generating a report, processing a video,
reconciling data overnight. The engine accepts that work, stores it, and runs it in the background.
It must not lose work, must not run the same work twice in a way that causes harm, and must recover
when something fails.

Each of those goals maps to a part of the code:

| Goal | Where it lives |
|------|----------------|
| Accept work quickly | `api/`, `messaging/publish/` |
| Don't lose work | Postgres `tasks` table, publish after commit |
| Don't run it twice harmfully | `idempotency/`, row locks in `task/` |
| Recover from failures | `messaging/retry/`, `messaging/topology/` |
| Run work on a schedule | `schedule/` |
| Keep the code organized | `*ArchitectureRulesTest` |

---

## 2. Reading order

Read the code in this order. Each step depends on the ones before it.

1. **`task/domain/`**: the core concepts. Start with `TaskStatus`, then `TaskTransitions`, then `Task`.
2. **`task/handler/`**: the extension point where real work is written.
3. **`task/repository/` and `task/service/`**: how state changes are stored safely.
4. **`idempotency/`**: how duplicate deliveries are filtered.
5. **`messaging/topology/`**: the RabbitMQ layout. Read `TopologyConstants` first, then `RabbitTopology`.
6. **`messaging/retry/`**: the backoff schedule and how retries are routed.
7. **`messaging/consume/TaskMessageListener`**: the worker. This is where everything above is used together.
8. **`messaging/publish/`**: how tasks get into the system.
9. **`api/`**: the HTTP entry points.
10. **`schedule/`**: recurring work, built on top of publishing.
11. **`config/`**: wiring and settings. Read it last, because it only connects what you already know.

---

## 3. The domain: `task/domain/`

### `TaskStatus`
Five values: `PENDING`, `PROCESSING`, `RETRYING`, `COMPLETED`, `FAILED`. A task always has exactly
one of these.

### `TaskTransitions`
This class answers one question: is a status change legal? It holds a table of allowed moves. For
example, `COMPLETED` can move nowhere, and `FAILED` can only return to `PENDING` for an operator
replay.

It also answers "may this worker start the task now?" A task can start if it is waiting, or if it
is `PROCESSING` but its lease has expired. The lease exists because a worker can crash in the
middle of a task. Without it, the task would be stuck forever.

**Why it's separate:** every rule is in one file. If you want to know whether a transition is
allowed, you look here and nowhere else.

### `Task`
The JPA entity that maps to the `tasks` table. Its public methods (`startProcessing`,
`markCompleted`, `markRetrying`, `markFailed`) all call `transitionTo`, which asks
`TaskTransitions` before changing the status. So the entity can never reach an illegal state, even
if calling code is careless.

The `@Version` field is optimistic locking, a backup for the pessimistic lock in the repository.

**Notice what `Task` does not import:** nothing from messaging. That is enforced by a test.

---

## 4. Handlers: `task/handler/`

This is where the actual work happens, and it is the part you will change most often.

- **`TaskHandler`** is an interface with two methods: `type()` returns the name, such as
  `report.generate`, and `handle(message)` does the work.
- **`TaskHandlerRegistry`** collects every handler Spring finds and maps type to handler. It uses
  `toUnmodifiableMap`, which throws at startup if two handlers claim the same type. That turns a
  silent runtime bug into a startup error.
- **`EchoTaskHandler`** is a working example. It only logs, but it shows the whole pattern.

**The rules for a handler** (these matter more than any other part of the design):

1. **Be idempotent.** The engine delivers at least once, so `handle` may run twice for one task.
   Writing a file with a fixed name is safe to repeat. Sending an email is not, unless you guard it.
2. **Throw `PermanentFailureException`** for bad input that retrying cannot fix. The task goes
   straight to the parking lot.
3. **Throw any other `RuntimeException`** for transient problems such as a timeout. The engine retries.

---

## 5. Storing state safely: `task/repository/` and `task/service/`

- **`TaskRepository.lockById`** runs `SELECT ... FOR UPDATE`. If two workers try to start the same
  task at the same moment, one waits until the other's transaction commits, then sees the new status.
- **`TaskStateService`** wraps every state change in a transaction and uses the lock. Its key method
  is `startProcessing`, which returns a `StartOutcome`:
    - `STARTED`: the row is now `PROCESSING`. This worker must run it.
    - `FINISHED`: the task is already `COMPLETED` or `FAILED`. Stop.
    - `HELD`: another worker holds a live lease. Come back later.

The distinction between `FINISHED` and `HELD` matters. Treating both as "do nothing" would lose the
task when another worker is still running it.

---

## 6. Deduplication: `idempotency/`

Redis is the first line of defense against duplicate deliveries. `IdempotencyGuard.claim` runs
`SET key NX`, which succeeds for exactly one caller, even when many arrive at the same moment.

It returns a `ClaimResult`:
- `CLAIMED`: you are the one worker that should run this.
- `ALREADY_DONE`: someone finished it. Acknowledge and move on.
- `IN_FLIGHT`: someone else is running it now. Come back later rather than dropping it.

Redis is fast but not authoritative. If it loses a key, Postgres still refuses to run a completed
task. So Redis prevents wasted work, and Postgres prevents wrong results.

---

## 7. The wiring of RabbitMQ: `messaging/topology/`

Start with `TopologyConstants`. It holds every exchange, queue, and routing-key name as a constant,
so a typo causes a compile error instead of creating a second queue.

`RabbitTopology` declares them. Here is the path a message takes:

```
tasks.exchange ─► tasks.main ─┬─► (success) acknowledged
                              ├─► (permanent failure / retries spent) ─► tasks.parking
                              └─► (transient failure) ─► tasks.retry.exchange
                                       ─► tasks.retry.tier.N ─(TTL expires)─► tasks.exchange
```

Two design choices to notice:

- **Retry waits in a queue, not in a thread.** A message sitting in `tasks.retry.tier.2` costs no
  memory on a worker. The broker holds it until the TTL expires, then sends it back. A worker that
  slept for 10 minutes would block that thread the whole time.
- **One queue per retry tier.** Quorum queues do not support per-message TTL, so each delay gets its
  own queue. Tier 0 waits 1 second, tier 4 waits 10 minutes.

Quorum queues are used throughout because they replicate across nodes, so a broker failure does not
lose messages.

---

## 8. Retries: `messaging/retry/`

- **`BackoffPolicy`** holds the delay list. Its size is the maximum number of retries. The constructor
  rejects empty lists and zero or negative delays. It's a small class, and a good one to start with.
- **`RetryRouter`** does the routing. It has three methods:
    - `hasBudget`: are retries left?
    - `scheduleRetry`: send the message to the next tier and count the attempt.
    - `defer`: send it to tier 0 without counting. Used when the task is busy or infrastructure is down.

The attempt number travels in the `x-attempt` message header. That is how the count survives
retries, since the message itself is the only thing that persists between attempts.

---

## 9. The worker: `messaging/consume/TaskMessageListener`

This is the most important class in the project, because it is where every earlier piece is used.
Read it top to bottom with the sections above open.

The method `onTask` does two things in order:

1. **`acquire`**: decide whether this worker should run the task.
    - Redis claim. Stop or defer if someone else has it.
    - Postgres start. Stop if finished. Defer if held by a live lease.
    - Any exception here (Redis or Postgres down) means defer, not park. Nothing ran, so there is
      nothing to record as a failure.
2. **`runHandler`**: run the work and record the outcome.
    - Success: mark completed, mark Redis done.
    - `PermanentFailureException`: mark failed and reject, which sends the message to the parking lot.
    - Other exception with retries left: publish the retry first, then mark `RETRYING`.
    - Other exception with no retries left: mark failed and park.

**The order of "publish, then record" in the retry path is deliberate.** If the database write
failed after a publish, the task stays `PROCESSING` and its lease expires, so the retry still
happens. The reverse order could leave a `RETRYING` row with no message behind it, and nothing would
ever pick it up.

The class has no business rules. It decides what happened to a message and calls the right service.
That keeps it small and makes the rules testable without a broker.

---

## 10. Getting tasks in: `messaging/publish/` and `api/`

**`TaskSubmissionService.submit`** does two things inside one transaction: it saves a `PENDING` row,
and it registers a callback. The callback publishes the message only after the transaction commits.

Publishing before commit would let a worker see a message for a row that does not exist yet. The
worker would then fail or do nothing, and the task would be lost.

**`TaskController`** is a thin HTTP layer. It validates the request, calls the service, and returns
`202 Accepted`, because the work happens later. `GET /api/tasks/{id}` reads the row directly.

**`ScheduleController`** creates schedules the same way.

Controllers do not contain logic. If you find a rule in a controller, move it to a service.

---

## 11. Recurring work: `schedule/`

This package is built on top of everything else. A schedule does not run work itself. It creates
tasks through the normal `submit` path, so scheduled work gets the same retries, idempotency, and
parking as any other task.

- **`Schedule`** holds a cron rule and computes the next run. Its `fire` method computes the next
  run from *now*, which means downtime causes one catch-up run, not a flood of missed runs.
- **`ScheduleRepository`** has one important query. `FOR UPDATE SKIP LOCKED` lets several engine
  instances poll at the same time without picking up the same schedule. An instance that finds a
  row locked skips it and moves on.
- **`ScheduleDispatcher`** runs on a timer. In one transaction, it creates a task for each due
  schedule and advances each schedule's next run. Either both happen or neither does, so a schedule
  can never fire without its task.

---

## 12. Configuration and startup: `config/` and `application.yml`

- **`EngineProperties`** is a record that maps every `engine.*` setting to a typed field. Read it
  to see every tunable in one place.
- **`EngineConfig`** creates the beans that Spring cannot build by itself: the clock, the JSON
  converter, the backoff policy, and the listener container. It enables scheduling.
- **`application.yml`** holds defaults. Each value can be overridden with an environment variable, so
  the same jar runs on your laptop and in production.
- **Flyway migrations** (`db/migration/`) create the tables at startup. Hibernate only checks that
  the schema matches the entities (`ddl-auto: validate`). Flyway owns the schema.

The listener container factory is named `taskListenerContainerFactory`, not Spring's default name.
Spring Boot refuses to silently replace beans, so a clash would stop startup. The explicit name
avoids that.

---

## 13. Tests and architecture rules

- **`ArchitectureRulesTest`** enforces the dependency rules from section 3: the domain may not
  import messaging, AMQP is used only in messaging and config, and fields may not be injected. If
  you break a rule, the build fails. This is the cheapest guardrail in the project, so keep it.
- **`BackoffPolicyTest`** and **`ScheduleTest`** test pure logic without a broker or database.

The parts that need real infrastructure, such as the listener and the topology, have no tests yet.
Testcontainers, which starts real Postgres, Redis, and RabbitMQ in Docker for a test, is the planned
way to cover them.

---

## 14. Following one task end to end

Suppose a client calls `POST /api/tasks` with type `demo.echo`:

1. `TaskController` validates the body and calls `TaskSubmissionService.submit`.
2. `submit` saves a `PENDING` row. The transaction commits, and the callback publishes the message.
3. RabbitMQ routes the message through `tasks.exchange` to `tasks.main`.
4. `TaskMessageListener.onTask` receives it. `acquire` claims the key in Redis, then calls
   `startProcessing`, which locks the row and returns `STARTED`.
5. `runHandler` finds `EchoTaskHandler` through the registry and calls `handle`.
6. `markCompleted` sets the status to `COMPLETED`. Redis is set to `DONE`.
7. The message is acknowledged. A duplicate later gets `ALREADY_DONE` from Redis and is dropped.
8. `GET /api/tasks/{id}` now returns `COMPLETED`.

Now change one thing: make the handler throw a timeout. Step 5 fails, `onTransientFailure` publishes
to tier 0, marks the row `RETRYING`, and the message comes back after one second with
`x-attempt = 1`. Trace that path through the code yourself. It's the best exercise in this project.

---

## 15. Exercises for learning the code

1. **Add a handler.** Create `demo.fail` that always throws a runtime exception. Watch the message
   move through the retry tiers and end up in the parking lot. Check the `attempts` and `lastError`
   columns along the way.
2. **Break a transition.** Call `markCompleted` on a `PENDING` task in a test, and confirm
   `TaskTransitions` rejects it.
3. **Trigger the architecture rule.** Import a RabbitMQ class into `task/domain/Task.java`, then run
   `mvn test` and read the failure message.
4. **Change the schedule.** Create an every-minute schedule (`0 * * * * *`) and watch `nextRunAt`
   advance in the database.
5. **Kill a worker mid-task.** Add a `Thread.sleep` in a handler, stop the process during the sleep,
   and watch another worker take over after the lease expires.

---

## 16. Where to change what

| I want to... | Change this |
|--------------|-------------|
| Add a workload | a new class in `task/handler/` |
| Change the retry schedule | `engine.retry-tiers` (no code change) |
| Change lease length | `engine.task-lease` |
| Add a new status | `TaskStatus`, then `TaskTransitions` |
| Change the message format | `TaskMessage`, carefully, because old messages stay in queues |
| Add a queue | `TopologyConstants`, then `RabbitTopology` |
| Add an HTTP endpoint | `api/`, calling a service |
| Add a recurring rule type | `schedule/` |

Changing `TaskMessage` deserves care. Messages already in queues still use the old format, so a
change must be backward compatible until those queues drain.

---

## 17. Known gaps

The gaps are listed in full in `DOCUMENTATION.md`, section 14. The two that matter most for
understanding the design are:

- **The publish-after-commit gap.** If RabbitMQ fails right after the database commit, the task stays
  `PENDING` with no message. The transactional outbox pattern closes it, and it is the next thing to build.
- **Handler idempotency is the handler author's job.** The engine guarantees at-least-once delivery,
  not exactly-once. No amount of engine code can make a non-idempotent handler safe.
