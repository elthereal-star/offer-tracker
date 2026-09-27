package com.offertracker;

import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.AiChatMessage;
import com.offertracker.entity.Company;
import com.offertracker.service.CompanyService;
import com.offertracker.service.JobApplicationService;
import com.offertracker.service.OpenAiCompatibleClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.io.ByteArrayOutputStream;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void savesAnswerAndRejectsFollowUpBeforeAiScore() throws Exception {
        long resumeId = uploadResume();
        when(ai.chat(any())).thenReturn("请介绍一个你使用 Spring Boot 解决复杂问题的项目。");
        String created = mockMvc.perform(post("/api/ai/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resumeId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);
        long sessionId = tree.path("data").path("id").asLong();
        long questionId = tree.path("data").path("questions").get(0).path("id").asLong();
        mockMvc.perform(put("/api/ai/interviews/{sessionId}/questions/{questionId}/answer", sessionId, questionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"我设计了缓存和降级方案。\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[0].answer").value("我设计了缓存和降级方案。"));
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/follow-up", sessionId, questionId))
                .andExpect(status().isConflict());
    }

    @Test
    void completesInterviewWithScoresFollowUpReportAndRestorableHistory() throws Exception {
        long resumeId = uploadResume();
        Company company = companyService.create(new com.offertracker.dto.CreateCompanyRequest("AI Target " + System.nanoTime(), null, null));
        long applicationId = applicationService.create(new CreateApplicationRequest(company.getId(), "高级 Java 工程师", "上海", "30-40K", "官网", null, null, null, null)).getId();
        when(ai.chat(any())).thenReturn(
                "请介绍一个你使用 Spring Boot 解决复杂问题的项目。",
                "{\"score\":88,\"feedback\":\"结构清晰，可补充量化结果。\"}",
                "你如何验证该方案的性能提升？",
                "{\"score\":92,\"feedback\":\"有数据意识，回答具体。\"}",
                "优势：表达清晰。待提升：补充量化成果。建议继续准备项目数据。"
        );

        String created = mockMvc.perform(post("/api/ai/interviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resumeId + ",\"applicationId\":" + applicationId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created);
        long sessionId = tree.path("data").path("id").asLong();
        long firstQuestionId = tree.path("data").path("questions").get(0).path("id").asLong();

        mockMvc.perform(put("/api/ai/interviews/{sessionId}/questions/{questionId}/answer", sessionId, firstQuestionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"我设计了缓存和降级方案。\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/evaluate", sessionId, firstQuestionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[0].score").value(88));

        String followedUp = mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/follow-up", sessionId, firstQuestionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[1].questionNo").value(2))
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/follow-up", sessionId, firstQuestionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(2));
        long secondQuestionId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(followedUp)
                .path("data").path("questions").get(1).path("id").asLong();
        mockMvc.perform(put("/api/ai/interviews/{sessionId}/questions/{questionId}/answer", sessionId, secondQuestionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"使用压测对比优化前后的延迟。\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/evaluate", sessionId, secondQuestionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions[1].score").value(92));

        mockMvc.perform(post("/api/ai/interviews/{sessionId}/finish", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.averageScore").value(90))
                .andExpect(jsonPath("$.data.report").value("优势：表达清晰。待提升：补充量化成果。建议继续准备项目数据。"));
        mockMvc.perform(get("/api/ai/interviews/{id}", sessionId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.questions.length()").value(2));
        mockMvc.perform(get("/api/ai/interviews").param("resumeId", String.valueOf(resumeId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(sessionId));
        mockMvc.perform(put("/api/ai/interviews/{sessionId}/questions/{questionId}/answer", sessionId, secondQuestionId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"修改已结束面试的回答。\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/evaluate", sessionId, secondQuestionId))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/ai/interviews/{sessionId}/questions/{questionId}/follow-up", sessionId, secondQuestionId))
                .andExpect(status().isConflict());

        ArgumentCaptor<List<AiChatMessage>> prompts = ArgumentCaptor.forClass(List.class);
        verify(ai, times(5)).chat(prompts.capture());
        String firstPrompt = prompts.getAllValues().get(0).get(1).content();
        assertTrue(firstPrompt.contains(company.getName()));
        assertTrue(firstPrompt.contains("高级 Java 工程师"));
        assertTrue(firstPrompt.contains("上海"));
        assertTrue(firstPrompt.contains("30-40K"));
    }

    private long uploadResume() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("file", "ai-test.pdf", MediaType.APPLICATION_PDF_VALUE, minimalPdf("Java Spring candidate"));
        String response = mockMvc.perform(multipart("/api/resumes").file(pdf)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("data").path("id").asLong();
    }

    private static byte[] minimalPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(); document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(20, 700); stream.showText(text); stream.endText();
            }
            document.save(output); return output.toByteArray();
        }
    }
}
