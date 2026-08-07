package com.offertracker.enums;

/**
 * 投递状态机。按求职流程的自然顺序声明，统计接口会按此顺序返回。
 */
public enum ApplicationStatus {
    SAVED("已收藏"),
    APPLIED("已投递"),
    WRITTEN_TEST("笔试"),
    INTERVIEWING("面试中"),
    OFFER("已拿 Offer"),
    REJECTED("未通过"),
    WITHDRAWN("已放弃");

    private final String label;

    ApplicationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
