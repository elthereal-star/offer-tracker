package com.offertracker.entity;
import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
@TableName("ai_interview_questions")
public class AiInterviewQuestion {
 @TableId(type=IdType.AUTO) private Long id; private Long sessionId; private Integer questionNo; private String content; private String answer; private LocalDateTime createdAt;
 public Long getId(){return id;} public void setId(Long v){id=v;} public Long getSessionId(){return sessionId;} public void setSessionId(Long v){sessionId=v;} public Integer getQuestionNo(){return questionNo;} public void setQuestionNo(Integer v){questionNo=v;} public String getContent(){return content;} public void setContent(String v){content=v;} public String getAnswer(){return answer;} public void setAnswer(String v){answer=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
