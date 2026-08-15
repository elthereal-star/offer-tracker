package com.offertracker.dto;

import java.util.List;

public record ImportPreview(
        boolean valid,
        int companyCount,
        int applicationCount,
        int interviewCount,
        List<String> errors
) {
}
