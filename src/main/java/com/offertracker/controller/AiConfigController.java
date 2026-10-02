package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.AiConfigRequest;
import com.offertracker.dto.AiConfigResponse;
import com.offertracker.service.AiConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/config")
public class AiConfigController {

    private final AiConfigService configService;

    public AiConfigController(AiConfigService configService) { this.configService = configService; }

    @GetMapping
    public ApiResponse<AiConfigResponse> view() { return ApiResponse.ok(configService.view()); }

    @PutMapping
    public ApiResponse<AiConfigResponse> save(@Valid @RequestBody AiConfigRequest request) {
        return ApiResponse.ok(configService.save(request));
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        configService.clear();
        return ApiResponse.ok(null);
    }
}
