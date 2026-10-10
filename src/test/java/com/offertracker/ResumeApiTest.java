package com.offertracker;

import com.offertracker.entity.Company;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.service.JobApplicationService;
import com.offertracker.service.CompanyService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "offer-tracker.storage.resume-dir=target/test-resumes")
@AutoConfigureMockMvc
@Transactional
class ResumeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired CompanyService companyService;
    @Autowired JobApplicationService applicationService;

    @Test
    void uploadsListsDownloadsAndDeletesPdfResume() throws Exception {
        Company company = companyService.create(new com.offertracker.dto.CreateCompanyRequest(
                "Resume API " + System.nanoTime(), null, null));
        long applicationId = applicationService.create(new CreateApplicationRequest(
                company.getId(), "Java Developer", null, null, "官网", null, null, null, null)).getId();
        MockMultipartFile pdf = new MockMultipartFile("file", "candidate.pdf", MediaType.APPLICATION_PDF_VALUE,
                minimalPdf("Candidate Java Spring experience"));

        String created = mockMvc.perform(multipart("/api/resumes")
                        .file(pdf)
                        .param("applicationId", String.valueOf(applicationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename").value("candidate.pdf"))
                .andExpect(jsonPath("$.data.extractedCharacterCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).path("data").path("id").asLong();

        mockMvc.perform(get("/api/resumes").param("applicationId", String.valueOf(applicationId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(id));
        mockMvc.perform(get("/api/resumes/{id}/file", id))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("candidate.pdf")));
        mockMvc.perform(delete("/api/resumes/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/resumes/{id}/file", id)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsNonPdfFiles() throws Exception {
        MockMultipartFile text = new MockMultipartFile("file", "resume.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());
        mockMvc.perform(multipart("/api/resumes").file(text))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(containsString("PDF")));
    }

    private static byte[] minimalPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(20, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
