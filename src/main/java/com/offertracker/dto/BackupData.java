package com.offertracker.dto;

import com.offertracker.entity.Company;
import com.offertracker.entity.InterviewRound;
import com.offertracker.entity.JobApplication;

import java.time.LocalDateTime;
import java.util.List;

public record BackupData(
        int version,
        LocalDateTime exportedAt,
        List<Company> companies,
        List<JobApplication> applications,
        List<InterviewRound> interviews
) {
}
