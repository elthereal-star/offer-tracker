package com.offertracker.dto;

public record ImportResult(
        int companiesCreated,
        int companiesReused,
        int applicationsCreated,
        int interviewsCreated
) {
}
