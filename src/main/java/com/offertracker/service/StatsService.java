package com.offertracker.service;

import com.offertracker.dto.StatsOverview;
import com.offertracker.common.CurrentUserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.mapper.InterviewRoundMapper;
import com.offertracker.mapper.JobApplicationMapper;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final JobApplicationMapper applicationMapper;
    private final InterviewRoundMapper interviewRoundMapper;

    public StatsService(JobApplicationMapper applicationMapper,
                        InterviewRoundMapper interviewRoundMapper) {
        this.applicationMapper = applicationMapper;
        this.interviewRoundMapper = interviewRoundMapper;
    }

    public StatsOverview overview() {
        LambdaQueryWrapper<JobApplication> applicationQuery = new LambdaQueryWrapper<>();
        if (CurrentUserContext.get() != null) applicationQuery.eq(JobApplication::getOwnerId, CurrentUserContext.get().id());
        List<JobApplication> all = applicationMapper.selectList(applicationQuery);
        long total = all.size();

        Map<ApplicationStatus, Long> counts = all.stream()
                .collect(Collectors.groupingBy(JobApplication::getStatus,
                        () -> new EnumMap<>(ApplicationStatus.class),
                        Collectors.counting()));
        // 按求职流程顺序输出，前端可直接用于漏斗图
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), counts.getOrDefault(status, 0L));
        }

        long interviewCount = all.isEmpty() ? 0 : interviewRoundMapper.selectCount(new LambdaQueryWrapper<InterviewRound>()
                .in(InterviewRound::getApplicationId, all.stream().map(JobApplication::getId).toList()));
        long offerCount = counts.getOrDefault(ApplicationStatus.OFFER, 0L);
        double offerRate = total == 0 ? 0.0 : (double) offerCount / total;
        return new StatsOverview(total, byStatus, interviewCount, offerCount, offerRate);
    }
}
