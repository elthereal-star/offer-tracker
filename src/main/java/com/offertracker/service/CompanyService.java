package com.offertracker.service;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.UpdateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.mapper.CompanyMapper;
import com.offertracker.mapper.JobApplicationMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CompanyService {

    private final CompanyMapper companyMapper;
    private final JobApplicationMapper applicationMapper;

    public CompanyService(CompanyMapper companyMapper, JobApplicationMapper applicationMapper) {
        this.companyMapper = companyMapper;
        this.applicationMapper = applicationMapper;
    }

    public Company create(CreateCompanyRequest request) {
        String name = request.name().trim();
        ensureNameAvailable(name, null);
        Company company = new Company();
        company.setName(name);
        company.setWebsite(trimToNull(request.website()));
        company.setNotes(trimToNull(request.notes()));
        company.setCreatedAt(LocalDateTime.now());
        companyMapper.insert(company);
        return company;
    }

    public List<Company> listAll() {
        return companyMapper.selectList(new LambdaQueryWrapper<Company>().orderByAsc(Company::getName));
    }

    public Company update(Long id, UpdateCompanyRequest request) {
        Company company = getOrThrow(id);
        String name = request.name().trim();
        ensureNameAvailable(name, id);
        company.setName(name);
        company.setWebsite(trimToNull(request.website()));
        company.setNotes(trimToNull(request.notes()));
        companyMapper.updateById(company);
        return company;
    }

    public void delete(Long id) {
        getOrThrow(id);
        Long applicationCount = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getCompanyId, id));
        if (applicationCount > 0) {
            throw new BusinessException(409, "该公司仍有关联投递，不能删除");
        }
        companyMapper.deleteById(id);
    }

    public Company getOrThrow(Long id) {
        Company company = companyMapper.selectById(id);
        if (company == null) {
            throw new BusinessException(404, "公司不存在: " + id);
        }
        return company;
    }

    private void ensureNameAvailable(String name, Long excludedId) {
        String normalized = name.toLowerCase(Locale.ROOT);
        boolean duplicate = companyMapper.selectList(null).stream()
                .anyMatch(company -> !company.getId().equals(excludedId)
                        && company.getName().trim().toLowerCase(Locale.ROOT).equals(normalized));
        if (duplicate) {
            throw new BusinessException(409, "公司名称已存在: " + name);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
