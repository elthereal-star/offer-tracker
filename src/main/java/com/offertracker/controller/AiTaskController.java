package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.AiTaskResponse;
import com.offertracker.dto.SubmitAiTaskRequest;
import com.offertracker.service.AiTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/tasks")
public class AiTaskController {
    private final AiTaskService tasks;
    public AiTaskController(AiTaskService tasks) { this.tasks = tasks; }

    @GetMapping("/{id}")
    public ApiResponse<AiTaskResponse> get(@PathVariable Long id) { return ApiResponse.ok(tasks.get(id)); }

    @PostMapping
    public ApiResponse<AiTaskResponse> submit(@Valid @RequestBody SubmitAiTaskRequest request) {
        return ApiResponse.ok(tasks.submit(request.taskType(), request.idempotencyKey(), request.payload()));
    }
}
