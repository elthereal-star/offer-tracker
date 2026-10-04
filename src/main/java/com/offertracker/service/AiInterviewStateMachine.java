package com.offertracker.service;

import com.offertracker.common.BusinessException;
import com.offertracker.enums.AiInterviewStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class AiInterviewStateMachine {
    private static final Map<AiInterviewStatus, Set<AiInterviewStatus>> TRANSITIONS =
            new EnumMap<>(AiInterviewStatus.class);

    static {
        TRANSITIONS.put(AiInterviewStatus.ACTIVE, EnumSet.of(AiInterviewStatus.COMPLETED));
        TRANSITIONS.put(AiInterviewStatus.COMPLETED, EnumSet.noneOf(AiInterviewStatus.class));
    }

    public void requireActive(String status) {
        if (AiInterviewStatus.parse(status) != AiInterviewStatus.ACTIVE) {
            throw new BusinessException(409, "AI 面试会话已结束");
        }
    }

    public void requireTransition(String source, AiInterviewStatus target) {
        AiInterviewStatus from = AiInterviewStatus.parse(source);
        if (from == null || target == null || !TRANSITIONS.getOrDefault(from, Set.of()).contains(target)) {
            throw new BusinessException(409, "不允许的 AI 面试状态转移: " + source + " -> " + target);
        }
    }
}
