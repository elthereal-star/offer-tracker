package com.offertracker.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.*;
import com.offertracker.entity.*;
import com.offertracker.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class AiInterviewService {
 private final AiInterviewSessionMapper sessions; private final AiInterviewQuestionMapper questions; private final ResumeService resumes; private final JobApplicationService applications; private final OpenAiCompatibleClient ai; private final ObjectMapper objectMapper;
 public AiInterviewService(AiInterviewSessionMapper s,AiInterviewQuestionMapper q,ResumeService r,JobApplicationService a,OpenAiCompatibleClient ai,ObjectMapper objectMapper){sessions=s;questions=q;resumes=r;applications=a;this.ai=ai;this.objectMapper=objectMapper;}
 @Transactional public AiInterviewSessionResponse create(CreateAiInterviewRequest req){
  Resume resume=resumes.getOrThrow(req.resumeId()); if(req.applicationId()!=null) applications.getOrThrow(req.applicationId());
  String context=resume.getExtractedText(); if(context.length()>12000) context=context.substring(0,12000);
  String prompt="你是一名专业技术面试官。请根据以下候选人简历"+(req.applicationId()!=null?"和目标投递岗位":"")+"生成第一道个性化面试题。只返回题目本身，不要编号、不要解释。简历：\n"+context;
  String content=ai.chat(List.of(new AiChatMessage("system","你负责设计严谨、友好的求职面试题。"),new AiChatMessage("user",prompt)));
  AiInterviewSession session=new AiInterviewSession(); session.setResumeId(req.resumeId()); session.setApplicationId(req.applicationId()); session.setStatus("ACTIVE"); session.setCreatedAt(LocalDateTime.now()); session.setUpdatedAt(LocalDateTime.now()); sessions.insert(session);
  AiInterviewQuestion question=new AiInterviewQuestion(); question.setSessionId(session.getId()); question.setQuestionNo(1); question.setContent(content.trim()); question.setCreatedAt(LocalDateTime.now()); questions.insert(question);
  return get(session.getId());
 }
 public AiInterviewSessionResponse get(Long id){ AiInterviewSession s=sessions.selectById(id); if(s==null) throw new BusinessException(404,"AI 面试会话不存在: "+id); List<AiInterviewQuestionResponse> qs=questions.selectList(new LambdaQueryWrapper<AiInterviewQuestion>().eq(AiInterviewQuestion::getSessionId,id).orderByAsc(AiInterviewQuestion::getQuestionNo)).stream().map(q->new AiInterviewQuestionResponse(q.getId(),q.getQuestionNo(),q.getContent(),q.getAnswer(),q.getScore(),q.getFeedback())).toList(); return new AiInterviewSessionResponse(s.getId(),s.getResumeId(),s.getApplicationId(),s.getStatus(),qs); }
 @Transactional public AiInterviewSessionResponse answer(Long sessionId, Long questionId, SubmitAiInterviewAnswerRequest req){
  AiInterviewSession session=sessions.selectById(sessionId); if(session==null) throw new BusinessException(404,"AI 面试会话不存在: "+sessionId);
  if(!"ACTIVE".equals(session.getStatus())) throw new BusinessException(409,"AI 面试会话已结束");
  AiInterviewQuestion question=questions.selectOne(new LambdaQueryWrapper<AiInterviewQuestion>().eq(AiInterviewQuestion::getId,questionId).eq(AiInterviewQuestion::getSessionId,sessionId));
  if(question==null) throw new BusinessException(404,"AI 面试题目不存在: "+questionId);
  question.setAnswer(req.answer().trim()); questions.updateById(question); session.setUpdatedAt(LocalDateTime.now()); sessions.updateById(session); return get(sessionId);
 }
 @Transactional public AiInterviewSessionResponse evaluate(Long sessionId, Long questionId){
  AiInterviewQuestion question=questions.selectOne(new LambdaQueryWrapper<AiInterviewQuestion>().eq(AiInterviewQuestion::getId,questionId).eq(AiInterviewQuestion::getSessionId,sessionId));
  if(question==null) throw new BusinessException(404,"AI 面试题目不存在: "+questionId);
  if(question.getAnswer()==null || question.getAnswer().isBlank()) throw new BusinessException(409,"请先提交回答");
  String prompt="请评价下面的面试回答，只返回 JSON，格式必须是 {\"score\":整数0到100,\"feedback\":\"简洁的中文反馈\"}。题目：\n"+question.getContent()+"\n回答：\n"+question.getAnswer();
  String raw=ai.chat(List.of(new AiChatMessage("system","你是一名严谨、友好的技术面试官。"),new AiChatMessage("user",prompt)));
  try { String json=raw.trim().replaceFirst("^```(?:json)?\\s*","").replaceFirst("\\s*```$",""); JsonNode node=objectMapper.readTree(json); int score=node.path("score").asInt(-1); String feedback=node.path("feedback").asText("").trim(); if(score<0||score>100||feedback.isBlank()) throw new IllegalArgumentException(); question.setScore(score); question.setFeedback(feedback); questions.updateById(question); return get(sessionId); }
  catch(Exception ex){ throw new BusinessException(502,"AI 评分返回格式无效，请重试"); }
 }
}
