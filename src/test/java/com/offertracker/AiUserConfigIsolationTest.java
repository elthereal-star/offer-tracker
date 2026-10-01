package com.offertracker;

import com.offertracker.common.CurrentUser;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.AiConfigRequest;
import com.offertracker.entity.User;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.AiConfigService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Transactional
class AiUserConfigIsolationTest {
    @Autowired UserMapper users;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiConfigService configs;

    @AfterEach
    void clearUserContext() { CurrentUserContext.clear(); }

    @Test
    void encryptsAndIsolatesKeysBetweenUsers() {
        User first = insertUser("1370000" + System.nanoTime());
        User second = insertUser("1360000" + System.nanoTime());
        String firstKey = "sk-first-user-secret";
        String secondKey = "sk-second-user-secret";

        CurrentUserContext.set(new CurrentUser(first.getId(), "USER"));
        configs.save(new AiConfigRequest("https://provider.example/v1", "model-a", firstKey));
        String storedCiphertext = jdbc.queryForObject("SELECT api_key_ciphertext FROM ai_user_configs WHERE user_id = ?", String.class, first.getId());
        assertFalse(storedCiphertext.contains(firstKey));
        assertEquals(firstKey, configs.requireStored().apiKey());

        CurrentUserContext.set(new CurrentUser(second.getId(), "USER"));
        assertFalse(configs.view().configured());
        configs.save(new AiConfigRequest("https://provider.example/v1", "model-b", secondKey));
        assertEquals(secondKey, configs.requireStored().apiKey());

        CurrentUserContext.set(new CurrentUser(first.getId(), "USER"));
        assertEquals("model-a", configs.requireStored().model());
        assertEquals(firstKey, configs.requireStored().apiKey());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ai_user_configs", Integer.class));
    }

    private User insertUser(String phone) {
        User user = new User(); user.setPhone(phone); user.setPasswordHash("test"); user.setRole("USER");
        user.setStatus("ACTIVE"); user.setCreatedAt(LocalDateTime.now()); user.setUpdatedAt(LocalDateTime.now());
        users.insert(user); return user;
    }
}
