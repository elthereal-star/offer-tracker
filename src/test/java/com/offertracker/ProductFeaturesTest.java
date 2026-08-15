package com.offertracker;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.BackupData;
import com.offertracker.dto.CreateApplicationRequest;
import com.offertracker.dto.CreateCompanyRequest;
import com.offertracker.dto.ImportPreview;
import com.offertracker.dto.ImportResult;
import com.offertracker.dto.UpdateApplicationRequest;
import com.offertracker.dto.UpdateCompanyRequest;
import com.offertracker.entity.Company;
import com.offertracker.entity.JobApplication;
import com.offertracker.enums.ApplicationStatus;
import com.offertracker.service.CompanyService;
import com.offertracker.service.DataTransferService;
import com.offertracker.service.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductFeaturesTest {

    @Autowired
    private CompanyService companyService;
    @Autowired
    private JobApplicationService applicationService;
    @Autowired
    private DataTransferService dataTransferService;

    @Test
    void searchesAndEditsApplicationsAcrossCompanyAndApplicationFields() {
        String suffix = String.valueOf(System.nanoTime());
        Company company = companyService.create(new CreateCompanyRequest(
                "Search Labs " + suffix, "https://example.com", null));
        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "Platform Engineer " + suffix, "深圳", "25k-35k",
                "内推", "https://example.com/job", LocalDate.of(2026, 8, 1), "初始备注", null));

        assertSingleResult(applicationService.page(1, 20, null, null,
                "search labs " + suffix, null, null, null, null), application.getId());
        assertSingleResult(applicationService.page(1, 20, null, null,
                "platform engineer " + suffix, null, null, null, null), application.getId());
        assertSingleResult(applicationService.page(1, 20, ApplicationStatus.APPLIED, null,
                null, "深圳", "内推", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
                application.getId());

        JobApplication updated = applicationService.update(application.getId(), new UpdateApplicationRequest(
                company.getId(), "Senior Platform Engineer", "上海", "30k-40k",
                "官网", "https://example.com/new-job", LocalDate.of(2026, 8, 2), "更新备注"));
        assertEquals("上海", updated.getCity());
        assertEquals("官网", updated.getSource());
        assertEquals("更新备注", applicationService.getOrThrow(application.getId()).getNotes());

        BusinessException invalidRange = assertThrows(BusinessException.class, () ->
                applicationService.page(1, 20, null, null, null, null, null,
                        LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 2)));
        assertEquals(400, invalidRange.getCode());
    }

    @Test
    void preventsDuplicateCompanyNamesAndDeletingLinkedCompanies() {
        String name = "Unique Company " + System.nanoTime();
        Company company = companyService.create(new CreateCompanyRequest(name, null, null));

        BusinessException duplicate = assertThrows(BusinessException.class, () ->
                companyService.create(new CreateCompanyRequest("  " + name.toUpperCase() + "  ", null, null)));
        assertEquals(409, duplicate.getCode());

        Company updated = companyService.update(company.getId(),
                new UpdateCompanyRequest(name + " Updated", " https://example.com ", " notes "));
        assertEquals("https://example.com", updated.getWebsite());

        JobApplication application = applicationService.create(new CreateApplicationRequest(
                company.getId(), "关联岗位", null, null, null, null, null, null, null));
        BusinessException linked = assertThrows(BusinessException.class, () -> companyService.delete(company.getId()));
        assertEquals(409, linked.getCode());

        applicationService.delete(application.getId());
        companyService.delete(company.getId());
        assertThrows(BusinessException.class, () -> companyService.getOrThrow(company.getId()));
    }

    @Test
    void validatesAndRestoresCompleteBackupsWithRemappedIds() {
        JobApplication orphan = new JobApplication();
        orphan.setId(10L);
        orphan.setCompanyId(999L);
        orphan.setPosition("Orphan");
        orphan.setStatus(ApplicationStatus.APPLIED);
        ImportPreview invalid = dataTransferService.validate(new BackupData(
                1, LocalDateTime.now(), List.of(), List.of(orphan), List.of()));
        assertFalse(invalid.valid());
        assertTrue(invalid.errors().stream().anyMatch(error -> error.contains("不存在的公司")));

        String suffix = String.valueOf(System.nanoTime());
        Company company = companyService.create(new CreateCompanyRequest("Backup " + suffix, null, null));
        applicationService.create(new CreateApplicationRequest(
                company.getId(), "Backup Position", null, null, null, null, null, null, null));

        BackupData backup = dataTransferService.exportBackup();
        ImportPreview preview = dataTransferService.validate(backup);
        assertTrue(preview.valid(), () -> String.join("; ", preview.errors()));

        ImportResult result = dataTransferService.importBackup(backup, true);
        assertEquals(preview.companyCount(), result.companiesCreated());
        assertEquals(preview.applicationCount(), result.applicationsCreated());
        assertEquals(preview.companyCount(), dataTransferService.exportBackup().companies().size());
        assertEquals(preview.applicationCount(), dataTransferService.exportBackup().applications().size());
        assertTrue(new String(dataTransferService.exportApplicationsCsv())
                .contains("Backup Position"));
    }

    private void assertSingleResult(Page<JobApplication> page, Long expectedId) {
        assertEquals(1, page.getTotal());
        assertEquals(expectedId, page.getRecords().getFirst().getId());
    }
}
