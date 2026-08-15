package com.offertracker.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.common.ApiResponse;
import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.UpdateInterviewResultRequest;
import com.offertracker.dto.UpdateApplicationRequest;
import com.offertracker.dto.UpdateStatusRequest;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.service.InterviewService;
import com.offertracker.service.JobApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/applications")
@Validated
public class JobApplicationController {

    private final JobApplicationService applicationService;
    private final InterviewService interviewService;

    public JobApplicationController(JobApplicationService applicationService,
                                    InterviewService interviewService) {
        this.applicationService = applicationService;
        this.interviewService = interviewService;
    }

    @PostMapping
    public ApiResponse<JobApplication> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ApiResponse.ok(applicationService.create(request));
    }

    @GetMapping
    public ApiResponse<Page<JobApplication>> page(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码不能小于 1") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "每页数量不能小于 1")
            @Max(value = 100, message = "每页数量不能超过 100") int size,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate appliedTo) {
        return ApiResponse.ok(applicationService.page(page, size, status, companyId,
                keyword, city, source, appliedFrom, appliedTo));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobApplication> detail(@PathVariable Long id) {
        return ApiResponse.ok(applicationService.getOrThrow(id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<JobApplication> updateStatus(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok(applicationService.updateStatus(id, request.status()));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobApplication> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateApplicationRequest request) {
        return ApiResponse.ok(applicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/interviews")
    public ApiResponse<InterviewRound> addInterview(@PathVariable Long id,
                                                    @Valid @RequestBody AddInterviewRequest request) {
        return ApiResponse.ok(interviewService.add(id, request));
    }

    @GetMapping("/{id}/interviews")
    public ApiResponse<List<InterviewRound>> listInterviews(@PathVariable Long id) {
        return ApiResponse.ok(interviewService.listByApplication(id));
    }

    @PutMapping("/interviews/{roundId}/result")
    public ApiResponse<InterviewRound> updateInterviewResult(
            @PathVariable Long roundId,
            @Valid @RequestBody UpdateInterviewResultRequest request) {
        return ApiResponse.ok(interviewService.updateResult(roundId, request));
    }
}
