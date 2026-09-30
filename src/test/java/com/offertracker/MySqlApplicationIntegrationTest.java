package com.offertracker;

import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.InterviewType;
import com.offertracker.service.CompanyService;
import com.offertracker.service.InterviewService;
import com.offertracker.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MySqlApplicationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.35")
            .withDatabaseName("offer_tracker")
            .withUsername("offer_test")
            .withPassword("offer_test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;
    @Autowired
    private InterviewService interviewService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migratesAndRunsApplicationFlowOnMySql() {
        Company company = companyService.create(new CreateCompanyRequest("MySQL 集成测试", null, null));
        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "集成测试岗位", null, null, null, null, null, null, null));

        InterviewRound first = interviewService.add(application.getId(),
                new AddInterviewRequest(InterviewType.VIDEO, null, null));
        InterviewRound second = interviewService.add(application.getId(),
                new AddInterviewRequest(InterviewType.HR, null, null));

        assertEquals(1, first.getRoundNo());
        assertEquals(2, second.getRoundNo());
        assertEquals("8", jdbcTemplate.queryForObject(
                "SELECT MAX(version) FROM flyway_schema_history WHERE success = 1", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO interview_rounds (application_id, round_no, type, result)
                VALUES (?, ?, ?, ?)
                """, application.getId(), 1, "HR", "PENDING"));
    }
}
