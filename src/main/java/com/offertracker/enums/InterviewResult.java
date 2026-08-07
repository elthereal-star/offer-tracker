package com.offertracker.enums;

public enum InterviewResult {
    PENDING("待结果"),
    PASS("通过"),
    FAIL("未通过");

    private final String label;

    InterviewResult(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
