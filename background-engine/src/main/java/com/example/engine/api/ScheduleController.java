package com.example.engine.api;

import com.example.engine.schedule.domain.Schedule;
import com.example.engine.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    public record CreateScheduleRequest(@NotBlank String tenantId,
                                        @NotBlank String taskType,
                                        @NotBlank String cron) {}

    public record ScheduleView(UUID id, String taskType, String cron, Instant nextRunAt) {}

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleView create(@Valid @RequestBody CreateScheduleRequest request) {
        Schedule schedule = service.create(request.tenantId(), request.taskType(), request.cron());
        return new ScheduleView(schedule.getId(), schedule.getTaskType(),
                schedule.getCronExpression(), schedule.getNextRunAt());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
