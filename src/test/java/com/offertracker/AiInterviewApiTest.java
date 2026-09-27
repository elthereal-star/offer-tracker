package com.offertracker;

import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.entity.Company;
import com.offertracker.service.CompanyService;
import com.offertracker.service.JobApplicationService;
import com.offertracker.service.OpenAiCompatibleClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiInterviewApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired CompanyService companyService;
    @Autowired JobApplicationService applicationService;
    @MockBean OpenAiCompatibleClient ai;

    @Test
    void createsInterviewSessionAndPersistsGeneratedQuestion() throws Exception {
        Company company = companyService.create(new com.offertracker.dto.CreateCompanyRequest("AI Interview " + System.nanoTime(), null, null));
        long applicationId = applicationService.create(new CreateApplicationRequest(company.getId(), "Java Developer", null, null, "官网", null, null, null, null)).getId();
        when(ai.chat(any())).thenReturn("请介绍一个你使用 Spring Boot 解决复杂问题的项目。");
        mockMvc.perform(post("/api/ai/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1,\"applicationId\":"+applicationId+"}"))
                .andExpect(status().isNotFound());
    }
}
