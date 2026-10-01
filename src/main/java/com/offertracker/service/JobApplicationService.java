package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.UpdateApplicationRequest;
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
        ApplicationStatus status = request.status() == null ? ApplicationStatus.APPLIED : request.status();
        String position = normalizePosition(request.position(), status);

        JobApplication application = new JobApplication();
        if (CurrentUserContext.get() != null) application.setOwnerId(CurrentUserContext.get().id());
        application.setCompanyId(request.companyId());
        application.setPosition(position);
        application.setCity(request.city());
        application.setSalaryRange(request.salaryRange());
        application.setSource(request.source());
        application.setJobUrl(request.jobUrl());
        application.setNotes(request.notes());
        application.setStatus(status);
        application.setAppliedAt(status == ApplicationStatus.SAVED
                ? request.appliedAt()
                : (request.appliedAt() != null ? request.appliedAt() : LocalDate.now()));
        application.setCreatedAt(LocalDateTime.now());
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.insert(application);
        return application;
    }

    public Page<JobApplication> page(int pageNum, int pageSize, ApplicationStatus status, Long companyId,
                                     String keyword, String city, String source,
                                     LocalDate appliedFrom, LocalDate appliedTo) {
        if (appliedFrom != null && appliedTo != null && appliedFrom.isAfter(appliedTo)) {
            throw new BusinessException(400, "投递开始日期不能晚于结束日期");
        }
        Long ownerId = CurrentUserContext.get() == null ? null : CurrentUserContext.get().id();
        return applicationMapper.selectFilteredPage(new Page<>(pageNum, pageSize), status, ownerId, companyId,
                trimToNull(keyword), trimToNull(city), trimToNull(source), appliedFrom, appliedTo);
    }

    public JobApplication getOrThrow(Long id) {
        JobApplication application = applicationMapper.selectById(id);
        if (application == null) {
            throw new BusinessException(404, "投递记录不存在: " + id);
        }
        if (CurrentUserContext.get() != null && !CurrentUserContext.get().id().equals(application.getOwnerId())) {
            throw new BusinessException(404, "投递记录不存在: " + id);
        }
        return application;
    }

    public void lockOrThrow(Long id) {
        if (applicationMapper.selectIdForUpdate(id) == null) {
            throw new BusinessException(404, "投递记录不存在: " + id);
        }
    }

    public JobApplication updateStatus(Long id, ApplicationStatus status) {
        JobApplication application = getOrThrow(id);
        if (status != ApplicationStatus.SAVED && isBlank(application.getPosition())) {
            throw new BusinessException(400, "请先补充岗位信息再调整投递状态");
        }
        application.setStatus(status);
        if (status == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(LocalDate.now());
        }
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        return application;
    }

    public void moveToInterviewingIfPreInterview(Long id) {
        JobApplication application = getOrThrow(id);
        if (application.getStatus() == ApplicationStatus.SAVED
                || application.getStatus() == ApplicationStatus.APPLIED
                || application.getStatus() == ApplicationStatus.WRITTEN_TEST) {
            if (isBlank(application.getPosition())) {
                throw new BusinessException(400, "请先补充岗位信息再添加面试");
            }
            application.setStatus(ApplicationStatus.INTERVIEWING);
            application.setUpdatedAt(LocalDateTime.now());
            applicationMapper.updateById(application);
        }
    }

    public void moveToRejected(Long id) {
        JobApplication application = getOrThrow(id);
        application.setStatus(ApplicationStatus.REJECTED);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
    }

    public JobApplication update(Long id, UpdateApplicationRequest request) {
        JobApplication application = getOrThrow(id);
        companyService.getOrThrow(request.companyId());
        application.setCompanyId(request.companyId());
        application.setPosition(normalizePosition(request.position(), application.getStatus()));
        application.setCity(trimToNull(request.city()));
        application.setSalaryRange(trimToNull(request.salaryRange()));
        application.setSource(trimToNull(request.source()));
        application.setJobUrl(trimToNull(request.jobUrl()));
        application.setAppliedAt(request.appliedAt());
        application.setNotes(trimToNull(request.notes()));
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizePosition(String position, ApplicationStatus status) {
        String normalized = position == null ? "" : position.trim();
        if (status != ApplicationStatus.SAVED && normalized.isBlank()) {
            throw new BusinessException(400, "岗位名称不能为空");
        }
        return normalized;
    }
}
