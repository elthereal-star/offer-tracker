package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.UpdateInterviewResultRequest;
import com.offertracker.entity.InterviewRound;
import com.offertracker.enums.InterviewResult;
import com.offertracker.mapper.InterviewRoundMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewService {

    private final InterviewRoundMapper interviewRoundMapper;
    private final JobApplicationService applicationService;

    public InterviewService(InterviewRoundMapper interviewRoundMapper,
                            JobApplicationService applicationService) {
        this.interviewRoundMapper = interviewRoundMapper;
        this.applicationService = applicationService;
    }

    public InterviewRound add(Long applicationId, AddInterviewRequest request) {
        applicationService.getOrThrow(applicationId);

        InterviewRound round = new InterviewRound();
        round.setApplicationId(applicationId);
        round.setRoundNo(request.roundNo());
        round.setType(request.type());
        round.setScheduledAt(request.scheduledAt());
        round.setFeedback(request.feedback());
        round.setResult(InterviewResult.PENDING);
        round.setCreatedAt(LocalDateTime.now());
        interviewRoundMapper.insert(round);
        return round;
    }

    public List<InterviewRound> listByApplication(Long applicationId) {
        return interviewRoundMapper.selectList(new LambdaQueryWrapper<InterviewRound>()
                .eq(InterviewRound::getApplicationId, applicationId)
                .orderByAsc(InterviewRound::getRoundNo));
    }

    public InterviewRound updateResult(Long roundId, UpdateInterviewResultRequest request) {
        InterviewRound round = interviewRoundMapper.selectById(roundId);
        if (round == null) {
            throw new BusinessException(404, "面试记录不存在: " + roundId);
        }
        round.setResult(request.result());
        if (request.feedback() != null) {
            round.setFeedback(request.feedback());
        }
        interviewRoundMapper.updateById(round);
        return round;
    }
}
