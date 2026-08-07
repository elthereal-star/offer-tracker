package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.StatsOverview;
import com.offertracker.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatsOverview> overview() {
        return ApiResponse.ok(statsService.overview());
    }
}
