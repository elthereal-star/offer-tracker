package com.offertracker.dto;

import java.util.Map;

/**
 * @param total        投递总数
 * @param byStatus     各状态的投递数量（按求职流程顺序）
 * @param interviewCount 面试轮次总数
 * @param offerCount   Offer 数
 * @param offerRate    Offer 率（0~1）
 */
public record StatsOverview(
        long total,
        Map<String, Long> byStatus,
        long interviewCount,
        long offerCount,
        double offerRate
) {
}
