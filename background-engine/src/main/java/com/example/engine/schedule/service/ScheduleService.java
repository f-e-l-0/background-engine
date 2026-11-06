package com.example.engine.schedule.service;

import com.example.engine.schedule.domain.Schedule;
import com.example.engine.schedule.repository.ScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class ScheduleService {

    private final ScheduleRepository schedules;
    private final Clock clock;

    public ScheduleService(ScheduleRepository schedules, Clock clock) {
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional
    public Schedule create(String tenantId, String taskType, String cron) {
        return schedules.save(Schedule.create(UUID.randomUUID(), tenantId, taskType, cron, clock.instant()));
    }
}
