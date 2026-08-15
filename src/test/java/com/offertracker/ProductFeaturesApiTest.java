package com.offertracker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.service.CompanyService;
import com.offertracker.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductFeaturesApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;

    @Test
    void filtersAndUpdatesApplicationsThroughHttpApi() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        Company company = companyService.create(new CreateCompanyRequest("API Search " + suffix, null, null));
        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "API Position " + suffix, "杭州", null, "官网", null,
                LocalDate.of(2026, 8, 5), null, null));

        mockMvc.perform(get("/api/applications")
                        .param("keyword", "api search " + suffix)
                        .param("city", "杭州")
                        .param("source", "官网")
                        .param("appliedFrom", "2026-08-05")
                        .param("appliedTo", "2026-08-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(application.getId()));

        mockMvc.perform(put("/api/applications/{id}", application.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyId", company.getId(),
                                "position", "Updated API Position",
                                "city", "上海",
                                "source", "内推",
                                "appliedAt", "2026-08-06"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.position").value("Updated API Position"))
                .andExpect(jsonPath("$.data.city").value("上海"));
    }

    @Test
    void exposesCompanyConflictsAndDownloadableExports() throws Exception {
        String name = "HTTP Company " + System.nanoTime();
        companyService.create(new CreateCompanyRequest(name, null, null));

        mockMvc.perform(post("/api/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name.toUpperCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(get("/api/data/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.companies").isArray());

        mockMvc.perform(get("/api/data/export.csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("公司,岗位")));
    }

    @Test
    void returnsDetailedImportPreviewWithoutWritingData() throws Exception {
        String invalidBackup = """
                {
                  "version": 1,
                  "companies": [],
                  "applications": [{
                    "id": 10,
                    "companyId": 999,
                    "position": "Orphan",
                    "status": "APPLIED"
                  }],
                  "interviews": []
                }
                """;

        mockMvc.perform(post("/api/data/import/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBackup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false))
                .andExpect(jsonPath("$.data.errors[0]").isNotEmpty());
    }

    @Test
    void persistsSavedAndInterviewStatusTransitionsThroughHttpApi() throws Exception {
        Company company = companyService.create(new CreateCompanyRequest(
                "HTTP Status Company " + System.nanoTime(), null, null));

        String savedResponse = mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyId", company.getId(),
                                "status", "SAVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAVED"))
                .andReturn().getResponse().getContentAsString();
        long savedId = objectMapper.readTree(savedResponse).path("data").path("id").asLong();

        String applicationResponse = mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "companyId", company.getId(),
                                "position", "HTTP Interview Position"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPLIED"))
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(applicationResponse).path("data").path("id").asLong();

        String roundResponse = mockMvc.perform(post("/api/applications/{id}/interviews", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "VIDEO"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long roundId = objectMapper.readTree(roundResponse).path("data").path("id").asLong();

        mockMvc.perform(get("/api/applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INTERVIEWING"));

        mockMvc.perform(put("/api/applications/interviews/{roundId}/result", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("result", "FAIL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("FAIL"));

        mockMvc.perform(get("/api/applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(get("/api/applications/{id}", savedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SAVED"));
    }
}
