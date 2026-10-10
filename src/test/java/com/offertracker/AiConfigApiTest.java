package com.offertracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "offer-tracker.storage.ai-config-file=target/test-ai-config.json")
@AutoConfigureMockMvc
@Transactional
class AiConfigApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void savesMasksAndClearsLocalAiConfiguration() throws Exception {
        Path path = Path.of("target/test-ai-config.json");
        Files.deleteIfExists(path);
        mockMvc.perform(put("/api/ai/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseUrl", "https://api.example.com/v1/",
                                "model", "deepseek-chat",
                                "apiKey", "sk-test-123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.baseUrl").value("https://api.example.com/v1"))
                .andExpect(jsonPath("$.data.maskedApiKey").value("sk-t••••3456"));
        String raw = Files.readString(path);
        org.junit.jupiter.api.Assertions.assertTrue(raw.contains("sk-test-123456"));
        mockMvc.perform(get("/api/ai/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedApiKey").value("sk-t••••3456"));
        mockMvc.perform(delete("/api/ai/config")).andExpect(status().isOk());
        mockMvc.perform(get("/api/ai/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));
    }
}
