package com.offertracker.dto;

import com.offertracker.entity.Resume;

import java.time.LocalDateTime;

public record ResumeResponse(
        Long id,
        Long applicationId,
        String originalFilename,
        String contentType,
        long sizeBytes,
        int extractedCharacterCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ResumeResponse from(Resume resume) {
        String text = resume.getExtractedText();
        return new ResumeResponse(
                resume.getId(),
                resume.getApplicationId(),
                resume.getOriginalFilename(),
                resume.getContentType(),
                resume.getSizeBytes(),
                text == null ? 0 : text.length(),
                resume.getCreatedAt(),
                resume.getUpdatedAt());
    }
}
