package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.BackupData;
import com.offertracker.dto.ImportPreview;
import com.offertracker.dto.ImportResult;
import com.offertracker.entity.Company;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.mapper.CompanyMapper;
import com.offertracker.mapper.InterviewRoundMapper;
import com.offertracker.mapper.JobApplicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DataTransferService {

    private static final int BACKUP_VERSION = 1;

    private final CompanyMapper companyMapper;
    private final JobApplicationMapper applicationMapper;
    private final InterviewRoundMapper interviewMapper;

    public DataTransferService(CompanyMapper companyMapper,
                               JobApplicationMapper applicationMapper,
                               InterviewRoundMapper interviewMapper) {
        this.companyMapper = companyMapper;
        this.applicationMapper = applicationMapper;
        this.interviewMapper = interviewMapper;
    }

    public BackupData exportBackup() {
        return new BackupData(
                BACKUP_VERSION,
                LocalDateTime.now(),
                companyMapper.selectList(ownerQuery(new LambdaQueryWrapper<Company>(), Company::getOwnerId).orderByAsc(Company::getId)),
                applicationMapper.selectList(ownerQuery(new LambdaQueryWrapper<JobApplication>(), JobApplication::getOwnerId).orderByAsc(JobApplication::getId)),
                interviewMapper.selectList(new LambdaQueryWrapper<InterviewRound>().orderByAsc(InterviewRound::getId))
        );
    }

    public byte[] exportApplicationsCsv() {
        Map<Long, String> companyNames = new HashMap<>();
        companyMapper.selectList(ownerQuery(new LambdaQueryWrapper<Company>(), Company::getOwnerId)).forEach(company -> companyNames.put(company.getId(), company.getName()));
        StringBuilder csv = new StringBuilder("\uFEFF公司,岗位,城市,薪资范围,状态,渠道,岗位链接,投递日期,备注\r\n");
        applicationMapper.selectList(ownerQuery(new LambdaQueryWrapper<JobApplication>()
                        .orderByDesc(JobApplication::getUpdatedAt), JobApplication::getOwnerId))
                .forEach(application -> csv.append(csvRow(
                        companyNames.get(application.getCompanyId()),
                        application.getPosition(),
                        application.getCity(),
                        application.getSalaryRange(),
                        application.getStatus() == null ? null : application.getStatus().name(),
                        application.getSource(),
                        application.getJobUrl(),
                        application.getAppliedAt() == null ? null : application.getAppliedAt().toString(),
                        application.getNotes())));
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public ImportPreview validate(BackupData backup) {
        List<String> errors = new ArrayList<>();
        if (backup == null) {
            return new ImportPreview(false, 0, 0, 0, List.of("备份内容不能为空"));
        }
        List<Company> companies = safe(backup.companies());
        List<JobApplication> applications = safe(backup.applications());
        List<InterviewRound> interviews = safe(backup.interviews());
        if (backup.version() != BACKUP_VERSION) errors.add("不支持的备份版本: " + backup.version());

        Set<Long> companyIds = new HashSet<>();
        Set<String> companyNames = new HashSet<>();
        for (int i = 0; i < companies.size(); i++) {
            Company company = companies.get(i);
            if (company == null || company.getId() == null) {
                errors.add("第 " + (i + 1) + " 个公司缺少 ID");
                continue;
            }
            if (!companyIds.add(company.getId())) errors.add("公司 ID 重复: " + company.getId());
            if (company.getName() == null || company.getName().isBlank()) {
                errors.add("公司 " + company.getId() + " 缺少名称");
            } else if (!companyNames.add(normalize(company.getName()))) {
                errors.add("公司名称重复: " + company.getName().trim());
            }
        }

        Set<Long> applicationIds = new HashSet<>();
        for (int i = 0; i < applications.size(); i++) {
            JobApplication application = applications.get(i);
            if (application == null || application.getId() == null) {
                errors.add("第 " + (i + 1) + " 条投递缺少 ID");
                continue;
            }
            if (!applicationIds.add(application.getId())) errors.add("投递 ID 重复: " + application.getId());
            if (!companyIds.contains(application.getCompanyId())) {
                errors.add("投递 " + application.getId() + " 引用了不存在的公司");
            }
            if (application.getPosition() == null || application.getPosition().isBlank()) {
                errors.add("投递 " + application.getId() + " 缺少岗位");
            }
            if (application.getStatus() == null) errors.add("投递 " + application.getId() + " 缺少状态");
        }

        Set<Long> interviewIds = new HashSet<>();
        Set<String> roundKeys = new HashSet<>();
        for (int i = 0; i < interviews.size(); i++) {
            InterviewRound interview = interviews.get(i);
            if (interview == null || interview.getId() == null) {
                errors.add("第 " + (i + 1) + " 条面试记录缺少 ID");
                continue;
            }
            if (!interviewIds.add(interview.getId())) errors.add("面试记录 ID 重复: " + interview.getId());
            if (!applicationIds.contains(interview.getApplicationId())) {
                errors.add("面试记录 " + interview.getId() + " 引用了不存在的投递");
            }
            if (interview.getRoundNo() == null || interview.getRoundNo() < 1) {
                errors.add("面试记录 " + interview.getId() + " 的轮次无效");
            } else if (!roundKeys.add(interview.getApplicationId() + ":" + interview.getRoundNo())) {
                errors.add("投递 " + interview.getApplicationId() + " 的面试轮次重复");
            }
            if (interview.getType() == null || interview.getResult() == null) {
                errors.add("面试记录 " + interview.getId() + " 缺少类型或结果");
            }
        }
        return new ImportPreview(errors.isEmpty(), companies.size(), applications.size(), interviews.size(), errors);
    }

    @Transactional
    public ImportResult importBackup(BackupData backup, boolean replaceExisting) {
        ImportPreview preview = validate(backup);
        if (!preview.valid()) {
            throw new BusinessException(400, "备份校验失败: " + String.join("；", preview.errors()));
        }
        if (replaceExisting) {
            interviewMapper.delete(null);
            applicationMapper.delete(null);
            companyMapper.delete(null);
        }

        Map<String, Company> existingCompanies = new HashMap<>();
        companyMapper.selectList(null).forEach(company -> existingCompanies.put(normalize(company.getName()), company));
        Map<Long, Long> companyIdMap = new HashMap<>();
        int companiesCreated = 0;
        int companiesReused = 0;
        for (Company source : safe(backup.companies())) {
            Company company = existingCompanies.get(normalize(source.getName()));
            if (company == null) {
                company = copyCompany(source);
                companyMapper.insert(company);
                existingCompanies.put(normalize(company.getName()), company);
                companiesCreated++;
            } else {
                companiesReused++;
            }
            companyIdMap.put(source.getId(), company.getId());
        }

        Map<Long, Long> applicationIdMap = new HashMap<>();
        for (JobApplication source : safe(backup.applications())) {
            JobApplication application = copyApplication(source, companyIdMap.get(source.getCompanyId()));
            applicationMapper.insert(application);
            applicationIdMap.put(source.getId(), application.getId());
        }
        for (InterviewRound source : safe(backup.interviews())) {
            interviewMapper.insert(copyInterview(source, applicationIdMap.get(source.getApplicationId())));
        }
        return new ImportResult(companiesCreated, companiesReused,
                preview.applicationCount(), preview.interviewCount());
    }

    private Company copyCompany(Company source) {
        Company target = new Company();
        target.setName(source.getName().trim());
        target.setWebsite(source.getWebsite());
        target.setNotes(source.getNotes());
        target.setCreatedAt(source.getCreatedAt() == null ? LocalDateTime.now() : source.getCreatedAt());
        return target;
    }

    private JobApplication copyApplication(JobApplication source, Long companyId) {
        JobApplication target = new JobApplication();
        target.setCompanyId(companyId);
        target.setPosition(source.getPosition().trim());
        target.setCity(source.getCity());
        target.setSalaryRange(source.getSalaryRange());
        target.setStatus(source.getStatus());
        target.setSource(source.getSource());
        target.setJobUrl(source.getJobUrl());
        target.setAppliedAt(source.getAppliedAt());
        target.setNotes(source.getNotes());
        target.setCreatedAt(source.getCreatedAt() == null ? LocalDateTime.now() : source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt() == null ? LocalDateTime.now() : source.getUpdatedAt());
        return target;
    }

    private InterviewRound copyInterview(InterviewRound source, Long applicationId) {
        InterviewRound target = new InterviewRound();
        target.setApplicationId(applicationId);
        target.setRoundNo(source.getRoundNo());
        target.setType(source.getType());
        target.setScheduledAt(source.getScheduledAt());
        target.setResult(source.getResult());
        target.setFeedback(source.getFeedback());
        target.setCreatedAt(source.getCreatedAt() == null ? LocalDateTime.now() : source.getCreatedAt());
        return target;
    }

    private String csvRow(Object... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            String value = values[i] == null ? "" : values[i].toString();
            if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
            row.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return row.append("\r\n").toString();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> LambdaQueryWrapper<T> ownerQuery(LambdaQueryWrapper<T> query, java.util.function.Function<T, ?> ignored) {
        if (CurrentUserContext.get() != null) query.apply("owner_id = {0}", CurrentUserContext.get().id());
        return query;
    }
}
