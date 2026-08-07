package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.mapper.InterviewRoundMapper;
import com.offertracker.mapper.JobApplicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class JobApplicationService {

    private final JobApplicationMapper applicationMapper;
    private final InterviewRoundMapper interviewRoundMapper;
    private final CompanyService companyService;

    public JobApplicationService(JobApplicationMapper applicationMapper,
                                 InterviewRoundMapper interviewRoundMapper,
                                 CompanyService companyService) {
        this.applicationMapper = applicationMapper;
        this.interviewRoundMapper = interviewRoundMapper;
        this.companyService = companyService;
    }

    public JobApplication create(CreateApplicationRequest request) {
        companyService.getOrThrow(request.companyId());

        JobApplication application = new JobApplication();
        application.setCompanyId(request.companyId());
        application.setPosition(request.position());
        application.setCity(request.city());
        application.setSalaryRange(request.salaryRange());
        application.setSource(request.source());
        application.setJobUrl(request.jobUrl());
        application.setNotes(request.notes());
        application.setStatus(ApplicationStatus.APPLIED);
        application.setAppliedAt(request.appliedAt() != null ? request.appliedAt() : LocalDate.now());
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.insert(application);
        return application;
    }

    public Page<JobApplication> page(int pageNum, int pageSize, ApplicationStatus status, Long companyId) {
        LambdaQueryWrapper<JobApplication> query = new LambdaQueryWrapper<JobApplication>()
                .eq(status != null, JobApplication::getStatus, status)
                .eq(companyId != null, JobApplication::getCompanyId, companyId)
                .orderByDesc(JobApplication::getUpdatedAt);
        return applicationMapper.selectPage(new Page<>(pageNum, pageSize), query);
    }

    public JobApplication getOrThrow(Long id) {
        JobApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(404, "投递记录不存在: " + id);
        }
        return application;
    }

    public JobApplication updateStatus(Long id, ApplicationStatus status) {
        JobApplication application = getOrThrow(id);
        application.setStatus(status);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        return application;
    }

    @Transactional
    public void delete(Long id) {
        getOrThrow(id);
        interviewRoundMapper.delete(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getApplicationId, id));
        applicationMapper.deleteById(id);
    }
}
