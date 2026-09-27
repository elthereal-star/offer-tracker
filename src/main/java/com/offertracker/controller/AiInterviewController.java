package com.offertracker.controller;
import com.offertracker.common.ApiResponse;
import com.offertracker.dto.*;
import com.offertracker.service.AiInterviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ai/interviews")
public class AiInterviewController {
 private final AiInterviewService service;
 public AiInterviewController(AiInterviewService s){service=s;}
 @PostMapping public ApiResponse<AiInterviewSessionResponse> create(@Valid @RequestBody CreateAiInterviewRequest r){return ApiResponse.ok(service.create(r));}
 @GetMapping("/{id}") public ApiResponse<AiInterviewSessionResponse> get(@PathVariable Long id){return ApiResponse.ok(service.get(id));}
 @PutMapping("/{sessionId}/questions/{questionId}/answer") public ApiResponse<AiInterviewSessionResponse> answer(@PathVariable Long sessionId,@PathVariable Long questionId,@Valid @RequestBody SubmitAiInterviewAnswerRequest r){return ApiResponse.ok(service.answer(sessionId,questionId,r));}
}
