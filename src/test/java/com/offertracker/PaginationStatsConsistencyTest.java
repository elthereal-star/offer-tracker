package com.offertracker;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.StatsOverview;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.service.CompanyService;
import com.offertracker.service.JobApplicationService;
import com.offertracker.service.StatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class PaginationStatsConsistencyTest {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;
    @Autowired
    private StatsService statsService;

    @Test
    void paginationAndStatsCoverTheSameApplications() {
        StatsOverview before = statsService.overview();
        Company company = companyService.create(new CreateCompanyRequest("Pagination Test", null, null));
        for (int index = 0; index < 105; index++) {
            JobApplication application = applicationService.create(new CreateApplicationRequest(
                    company.getId(), "Position " + index, null, null, null, null, null, null, null));
            if (index % 3 == 0) {
                applicationService.updateStatus(application.getId(), ApplicationStatus.INTERVIEWING);
            }
        }

        Page<JobApplication> firstPage = applicationService.page(
                1, 100, null, company.getId(), null, null, null, null, null);
        Page<JobApplication> secondPage = applicationService.page(
                2, 100, null, company.getId(), null, null, null, null, null);
        StatsOverview stats = statsService.overview();

        assertEquals(105, firstPage.getTotal());
        assertEquals(100, firstPage.getRecords().size());
        assertEquals(5, secondPage.getRecords().size());
        assertEquals(firstPage.getTotal(), stats.total() - before.total());
        assertEquals(35L,
                stats.byStatus().get("INTERVIEWING") - before.byStatus().get("INTERVIEWING"));
        assertEquals(70L,
                stats.byStatus().get("APPLIED") - before.byStatus().get("APPLIED"));
    }
}
