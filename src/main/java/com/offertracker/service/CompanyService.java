package com.offertracker.service;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.mapper.CompanyMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompanyService {

    private final CompanyMapper companyMapper;

    public CompanyService(CompanyMapper companyMapper) {
        this.companyMapper = companyMapper;
    }

    public Company create(CreateCompanyRequest request) {
        Company company = new Company();
        company.setName(request.name());
        company.setWebsite(request.website());
        company.setNotes(request.notes());
        company.setCreatedAt(LocalDateTime.now());
        companyMapper.insert(company);
        return company;
    }

    public List<Company> listAll() {
        return companyMapper.selectList(null);
    }

    public Company getOrThrow(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(404, "公司不存在: " + id);
        }
        return company;
    }
}
