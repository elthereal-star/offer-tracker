package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.entity.AiInterviewQuestion;
import com.offertracker.entity.AiInterviewSession;
import com.offertracker.entity.User;
import com.offertracker.mapper.AiInterviewQuestionMapper;
import com.offertracker.mapper.AiInterviewSessionMapper;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.AiInterviewService;
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
class AiInterviewOwnershipTest {
    @Autowired UserMapper users;
    @Autowired AiInterviewSessionMapper sessions;
    @Autowired AiInterviewQuestionMapper questions;
    @Autowired AiInterviewService service;

    @AfterEach
    void clearUserContext() {
        CurrentUserContext.clear();
    }

    @Test
    void hidesAnotherUsersInterviewSession() {
        User owner = insertUser("1380000" + System.nanoTime());
        User other = insertUser("1390000" + System.nanoTime());
        AiInterviewSession session = new AiInterviewSession();
        session.setOwnerId(owner.getId());
        session.setResumeId(1L);
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessions.insert(session);
        AiInterviewQuestion question = new AiInterviewQuestion();
        question.setSessionId(session.getId());
        question.setQuestionNo(1);
        question.setContent("测试题目");
        question.setCreatedAt(LocalDateTime.now());
        questions.insert(question);

        CurrentUserContext.set(new CurrentUser(other.getId(), "USER"));
        assertThrows(BusinessException.class, () -> service.get(session.getId()));
        assertEquals(0, service.list(null).size());

        CurrentUserContext.set(new CurrentUser(owner.getId(), "USER"));
        assertEquals(session.getId(), service.get(session.getId()).id());
    }

    private User insertUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setPasswordHash("test");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        users.insert(user);
        return user;
    }
}
