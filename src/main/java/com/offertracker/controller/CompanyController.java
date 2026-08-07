package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ApiResponse<Company> create(@Valid @RequestBody CreateCompanyRequest request) {
        return ApiResponse.ok(companyService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Company>> list() {
        return ApiResponse.ok(companyService.listAll());
    }
}
