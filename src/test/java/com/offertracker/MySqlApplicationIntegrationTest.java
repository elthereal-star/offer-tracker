package com.offertracker;

import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.RefreshTokenRequest;
import com.offertracker.common.BusinessException;
import com.offertracker.entity.AuthRefreshSession;
import com.offertracker.entity.Company;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.entity.User;
import com.offertracker.enums.InterviewType;
import com.offertracker.mapper.AuthRefreshSessionMapper;
import com.offertracker.mapper.UserMapper;
import com.offertracker.service.CompanyService;
import com.offertracker.service.IdentityService;
import com.offertracker.service.InterviewService;
import com.offertracker.service.JobApplicationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
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
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AuthRefreshSessionMapper sessionMapper;
    @Autowired
    private IdentityService identityService;

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
        assertEquals("13", jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO interview_rounds (application_id, round_no, type, result)
                VALUES (?, ?, ?, ?)
                """, application.getId(), 1, "HR", "PENDING"));
    }

    @Test
    void refreshTokenIsConsumedOnlyOnceOnMySql() throws Exception {
        User user = new User();
        user.setPhone("+8613900000001");
        user.setPasswordHash("unused");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        String refreshToken = "mysql-integration-refresh-token";
        AuthRefreshSession session = new AuthRefreshSession();
        session.setUserId(user.getId());
        session.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(refreshToken.getBytes(StandardCharsets.UTF_8))));
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session.setCreatedAt(LocalDateTime.now());
        sessionMapper.insert(session);

        assertEquals("Bearer", identityService.refresh(new RefreshTokenRequest(refreshToken)).tokenType());
        assertThrows(BusinessException.class, () -> identityService.refresh(new RefreshTokenRequest(refreshToken)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_sessions WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, user.getId()));
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_sessions WHERE user_id = ?",
                Integer.class, user.getId()));
    }
}
