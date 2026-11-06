package com.example.engine.api;

import com.example.engine.messaging.publish.TaskSubmissionService;
import com.example.engine.task.domain.Task;
import com.example.engine.task.domain.TaskStatus;
import com.example.engine.task.repository.TaskRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    public record SubmitRequest(@NotBlank String tenantId, @NotBlank String type) {}

    public record TaskView(UUID id, TaskStatus status, int attempts, String lastError) {}

    private final TaskSubmissionService submissions;
    private final TaskRepository tasks;

    public TaskController(TaskSubmissionService submissions, TaskRepository tasks) {
        this.submissions = submissions;
        this.tasks = tasks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> submit(@Valid @RequestBody SubmitRequest request) {
        return Map.of("taskId", submissions.submit(request.tenantId(), request.type()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskView> get(@PathVariable UUID id) {
        return tasks.findById(id)
                .map(this::toView)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private TaskView toView(Task task) {
        return new TaskView(task.getId(), task.getStatus(), task.getAttempts(), task.getLastError());
    }
}
