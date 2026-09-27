package com.offertracker.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("ai_interview_sessions")
public class AiInterviewSession {
 @TableId(type=IdType.AUTO) private Long id; private Long resumeId; private Long applicationId; private String status; private Integer averageScore; private String report; private LocalDateTime createdAt; private LocalDateTime updatedAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getResumeId(){return resumeId;} public void setResumeId(Long v){resumeId=v;} public Long getApplicationId(){return applicationId;} public void setApplicationId(Long v){applicationId=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
 public Integer getAverageScore(){return averageScore;} public void setAverageScore(Integer v){averageScore=v;} public String getReport(){return report;} public void setReport(String v){report=v;}
}
