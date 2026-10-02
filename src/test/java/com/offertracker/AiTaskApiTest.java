package com.offertracker;

import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiTaskApiTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mockMvc;
    @Autowired UserMapper users;

    @AfterEach void clear() { CurrentUserContext.clear(); }

    @Test
    void authenticatedSubmissionReturnsTask() throws Exception {
        User user = new User(); user.setPhone("+8613800088001"); user.setPasswordHash("unused"); user.setRole("USER"); user.setStatus("ACTIVE"); user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now()); users.insert(user);
        CurrentUserContext.set(new CurrentUser(user.getId(), "USER"));
        mockMvc.perform(post("/api/ai/tasks").contentType("application/json")
                        .content("{\"taskType\":\"GENERATE_QUESTION\",\"idempotencyKey\":\"api-1\",\"payload\":{\"resumeId\":7}}"))
                .andExpect(status().isOk());
    }
}
