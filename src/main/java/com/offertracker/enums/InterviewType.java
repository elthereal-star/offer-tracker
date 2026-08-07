package com.offertracker.enums;

public enum InterviewType {
    WRITTEN("笔试"),
    PHONE("电话面试"),
    VIDEO("视频面试"),
    ONSITE("现场面试"),
    HR("HR 面试");

    private final String label;

    InterviewType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
