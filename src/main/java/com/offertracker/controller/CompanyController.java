package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.UpdateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PutMapping("/{id}")
    public ApiResponse<Company> update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateCompanyRequest request) {
        return ApiResponse.ok(companyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return ApiResponse.ok(null);
    }
}
