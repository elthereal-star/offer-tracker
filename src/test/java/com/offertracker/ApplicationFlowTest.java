package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.dto.AddInterviewRequest;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.StatsOverview;
import com.offertracker.entity.Company;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.enums.InterviewResult;
import com.offertracker.enums.InterviewType;
import com.offertracker.service.CompanyService;
import com.offertracker.service.InterviewService;
import com.offertracker.service.JobApplicationService;
import com.offertracker.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖一条完整业务链路：建公司 -> 投递 -> 推进状态 -> 记录面试 -> 统计。
 */
@SpringBootTest
class ApplicationFlowTest {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;
    @Autowired
    private InterviewService interviewService;
    @Autowired
    private StatsService statsService;

    @Test
    void fullJobApplicationFlow() {
        Company company = companyService.create(new CreateCompanyRequest("示例科技", null, null));

        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "后端开发实习生", "北京", "300-400/天", "实习僧", null, null, null));
        assertEquals(ApplicationStatus.APPLIED, application.getStatus());

        applicationService.updateStatus(application.getId(), ApplicationStatus.INTERVIEWING);
        assertEquals(ApplicationStatus.INTERVIEWING,
                applicationService.getOrThrow(application.getId()).getStatus());

        InterviewRound round = interviewService.add(application.getId(),
                new AddInterviewRequest(1, InterviewType.VIDEO, null, "自我介绍 + 项目拷打"));
        assertEquals(InterviewResult.PENDING, round.getResult());

        StatsOverview stats = statsService.overview();
        assertEquals(1, stats.total());
        assertEquals(1L, stats.byStatus().get("INTERVIEWING"));
        assertEquals(1, stats.interviewCount());
    }

    @Test
    void rejectsApplicationForUnknownCompany() {
        CreateApplicationRequest request = new CreateApplicationRequest(
                999999L, "不存在的公司岗位", null, null, null, null, null, null);
        assertThrows(BusinessException.class, () -> applicationService.create(request));
    }
}
