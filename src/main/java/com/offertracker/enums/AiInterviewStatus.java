package com.offertracker.enums;

public enum AiInterviewStatus {
    ACTIVE,
    COMPLETED;

    public static AiInterviewStatus parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
