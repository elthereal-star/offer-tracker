package com.offertracker;

import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.service.CompanyService;
import com.offertracker.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class DatabaseIntegrityTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;

    @Test
    void rejectsApplicationWithUnknownCompany() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO job_applications (company_id, position, status)
                VALUES (?, ?, ?)
                """, 999999L, "无效岗位", "APPLIED"));
    }

    @Test
    void rejectsDuplicateRoundNumberAndCascadesInterviews() {
        Company company = companyService.create(new CreateCompanyRequest("约束测试公司", null, null));
        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "测试岗位", null, null, null, null, null, null, null));

        jdbcTemplate.update("""
                INSERT INTO interview_rounds (application_id, round_no, type, result)
                VALUES (?, ?, ?, ?)
                """, application.getId(), 1, "VIDEO", "PENDING");

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO interview_rounds (application_id, round_no, type, result)
                VALUES (?, ?, ?, ?)
                """, application.getId(), 1, "HR", "PENDING"));

        jdbcTemplate.update("DELETE FROM job_applications WHERE id = ?", application.getId());
        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM interview_rounds WHERE application_id = ?",
                Integer.class,
                application.getId());
        assertEquals(0, remaining);
    }
}
