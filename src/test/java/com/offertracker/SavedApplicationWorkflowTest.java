package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.UpdateApplicationRequest;
import com.offertracker.dto.UpdateInterviewResultRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.enums.InterviewType;
import com.offertracker.enums.InterviewResult;
import com.offertracker.service.CompanyService;
import com.offertracker.service.InterviewService;
import com.offertracker.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class SavedApplicationWorkflowTest {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;
    @Autowired
    private InterviewService interviewService;

    @Test
    void savesCompanyWithoutPositionAndRequiresPositionBeforeApplying() {
        Company company = companyService.create(new CreateCompanyRequest("收藏公司", null, null));
        JobApplication saved = applicationService.create(new CreateApplicationRequest(
                company.getId(), null, null, null, null, null, null, null, ApplicationStatus.SAVED));

        assertEquals(ApplicationStatus.SAVED, saved.getStatus());
        assertTrue(saved.getPosition().isEmpty());
        assertThrows(BusinessException.class,
                () -> applicationService.updateStatus(saved.getId(), ApplicationStatus.APPLIED));

        applicationService.update(saved.getId(), new UpdateApplicationRequest(
                company.getId(), "后端开发", null, null, null, null, null, null));
        JobApplication applied = applicationService.updateStatus(saved.getId(), ApplicationStatus.APPLIED);

        assertEquals(ApplicationStatus.APPLIED, applied.getStatus());
        assertNotNull(applied.getAppliedAt());
    }

    @Test
    void rejectsPositionlessNonSavedApplication() {
        Company company = companyService.create(new CreateCompanyRequest("普通投递公司", null, null));
        assertThrows(BusinessException.class, () -> applicationService.create(new CreateApplicationRequest(
                company.getId(), " ", null, null, null, null, null, null, ApplicationStatus.APPLIED)));
    }

    @Test
    void addingInterviewMovesPreInterviewStatusesButKeepsTerminalStatus() {
        Company company = companyService.create(new CreateCompanyRequest("面试流转公司", null, null));
        JobApplication applied = applicationService.create(new CreateApplicationRequest(
                company.getId(), "开发工程师", null, null, null, null, null, null, null));

        interviewService.add(applied.getId(), new AddInterviewRequest(InterviewType.VIDEO, null, "技术一面"));
        assertEquals(ApplicationStatus.INTERVIEWING, applicationService.getOrThrow(applied.getId()).getStatus());
        assertEquals("技术一面", interviewService.listByApplication(applied.getId()).getFirst().getFeedback());

        applicationService.updateStatus(applied.getId(), ApplicationStatus.OFFER);
        interviewService.add(applied.getId(), new AddInterviewRequest(InterviewType.HR, null, null));
        assertEquals(ApplicationStatus.OFFER, applicationService.getOrThrow(applied.getId()).getStatus());
        assertEquals(2, interviewService.listByApplication(applied.getId()).size());
    }

    @Test
    void failingAnInterviewMovesApplicationToRejected() {
        Company company = companyService.create(new CreateCompanyRequest("未通过流转公司", null, null));
        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "测试工程师", null, null, null, null, null, null, null));
        var round = interviewService.add(application.getId(),
                new AddInterviewRequest(InterviewType.VIDEO, null, null));

        interviewService.updateResult(round.getId(), new UpdateInterviewResultRequest(InterviewResult.FAIL, null));

        assertEquals(ApplicationStatus.REJECTED,
                applicationService.getOrThrow(application.getId()).getStatus());
    }
}
