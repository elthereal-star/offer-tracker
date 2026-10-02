package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.ResumeResponse;
import com.offertracker.service.ResumeService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@Validated
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) { this.resumeService = resumeService; }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ResumeResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Positive Long applicationId) {
        return ApiResponse.ok(resumeService.upload(file, applicationId));
    }

    @GetMapping
    public ApiResponse<List<ResumeResponse>> list(@RequestParam(required = false) @Positive Long applicationId) {
        return ApiResponse.ok(resumeService.list(applicationId));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable @Positive Long id) {
        ResumeService.ResumeFile file = resumeService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.resume().getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(file.content()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        resumeService.delete(id);
        return ApiResponse.ok(null);
    }
}
