package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.offertracker.entity.VerificationCode;
import com.offertracker.mapper.VerificationCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationCodeAttemptService {
    private final VerificationCodeMapper codes;

    public VerificationCodeAttemptService(VerificationCodeMapper codes) {
        this.codes = codes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long codeId) {
        codes.update(null, new LambdaUpdateWrapper<VerificationCode>()
                .eq(VerificationCode::getId, codeId)
                .isNull(VerificationCode::getConsumedAt)
                .lt(VerificationCode::getAttempts, 5)
                .setSql("attempts = attempts + 1"));
    }
}
