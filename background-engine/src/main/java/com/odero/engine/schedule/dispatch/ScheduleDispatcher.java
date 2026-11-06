package com.odero.engine.schedule.dispatch;

import com.odero.engine.messaging.publish.TaskSubmissionService;
import com.odero.engine.schedule.domain.Schedule;
import com.odero.engine.schedule.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Turns due schedules into tasks. One transaction covers three things: creating each task row,
 * advancing each schedule's nextRunAt, and the commit-time publish registered by submit().
 * Either all of it happens or none of it does, so a schedule cannot fire without its task.
 *
 * Known limit: the batch is one transaction. A schedule that always fails blocks the batch until
 * it is disabled. Isolating each schedule needs REQUIRES_NEW, which gives up batch atomicity.
 */
@Component
@ConditionalOnProperty(prefix = "engine.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScheduleDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ScheduleDispatcher.class);
    private static final int BATCH_SIZE = 50;

    private final ScheduleRepository schedules;
    private final TaskSubmissionService submissions;
    private final Clock clock;

    public ScheduleDispatcher(ScheduleRepository schedules, TaskSubmissionService submissions, Clock clock) {
        this.schedules = schedules;
        this.submissions = submissions;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${engine.scheduler.poll-interval-ms:5000}")
    @Transactional
    public void dispatchDue() {
        Instant now = clock.instant();
        List<Schedule> due = schedules.findDueForUpdate(now, BATCH_SIZE);
        for (Schedule schedule : due) {
            submissions.submit(schedule.getTenantId(), schedule.getTaskType());
            schedule.fire(now);
        }
        if (!due.isEmpty()) {
            log.info("dispatched {} due schedule(s)", due.size());
        }
    }
}
