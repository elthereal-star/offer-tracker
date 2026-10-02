package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.AiTaskResponse;
import com.offertracker.entity.AiTask;
import com.offertracker.entity.User;
import com.offertracker.mapper.AiTaskMapper;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.AiTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class AiTaskServiceTest {
    @Autowired AiTaskService tasks;
    @Autowired UserMapper users;
    @Autowired AiTaskMapper taskMapper;

    @AfterEach
    void clearContext() { CurrentUserContext.clear(); }

    @Test
    void submissionIsIdempotentAndOwnerScoped() {
        User first = user("+8613800099001");
        CurrentUserContext.set(new CurrentUser(first.getId(), "USER"));
        AiTaskResponse created = tasks.submit("GENERATE_QUESTION", "request-1", java.util.Map.of("resumeId", 7));
        AiTaskResponse repeated = tasks.submit("GENERATE_QUESTION", "request-1", java.util.Map.of("resumeId", 7));
        assertEquals(created.id(), repeated.id());
        AiTask persisted = taskMapper.selectById(created.id());
        assertEquals("NEW", persisted.getDispatchStatus());
        assertEquals("PENDING", persisted.getStatus());
        assertEquals(0, persisted.getAttempts());

        User second = user("+8613800099002");
        CurrentUserContext.set(new CurrentUser(second.getId(), "USER"));
        assertThrows(BusinessException.class, () -> tasks.get(created.id()));
    }

    @Test
    void rejectsUnsupportedTaskTypesAndInvalidPayloadsBeforePersistence() {
        User owner = user("+8613800099003");
        CurrentUserContext.set(new CurrentUser(owner.getId(), "USER"));

        assertThrows(BusinessException.class, () -> tasks.submit("UNKNOWN", "bad-type", java.util.Map.of()));
        assertThrows(BusinessException.class, () -> tasks.submit("GENERATE_QUESTION", "bad-id", java.util.Map.of("resumeId", 0)));
        assertThrows(BusinessException.class, () -> tasks.submit("FINISH_INTERVIEW", "extra", java.util.Map.of("sessionId", 1, "prompt", "unexpected")));
        assertThrows(BusinessException.class, () -> tasks.submit("FINISH_INTERVIEW", "x".repeat(129), java.util.Map.of("sessionId", 1)));
        assertThrows(BusinessException.class, () -> tasks.submit("FINISH_INTERVIEW", "large", java.util.Map.of("sessionId", "x".repeat(5000))));
    }

    private User user(String phone) {
        User user = new User(); user.setPhone(phone); user.setPasswordHash("unused");
        user.setRole("USER"); user.setStatus("ACTIVE"); user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        users.insert(user); return user;
    }
}
