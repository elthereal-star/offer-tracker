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
 @GetMapping public ApiResponse<java.util.List<AiInterviewSessionResponse>> list(@RequestParam(required = false) Long resumeId){return ApiResponse.ok(service.list(resumeId));}
 @PutMapping("/{sessionId}/questions/{questionId}/answer") public ApiResponse<AiInterviewSessionResponse> answer(@PathVariable Long sessionId,@PathVariable Long questionId,@RequestHeader(value="X-Idempotency-Key", required=false) String requestKey,@Valid @RequestBody SubmitAiInterviewAnswerRequest r){return ApiResponse.ok(service.answer(sessionId,questionId,requestKey,r));}
 @PostMapping("/{sessionId}/questions/{questionId}/evaluate") public ApiResponse<AiInterviewSessionResponse> evaluate(@PathVariable Long sessionId,@PathVariable Long questionId){return ApiResponse.ok(service.evaluate(sessionId,questionId));}
 @PostMapping("/{sessionId}/questions/{questionId}/follow-up") public ApiResponse<AiInterviewSessionResponse> followUp(@PathVariable Long sessionId,@PathVariable Long questionId){return ApiResponse.ok(service.followUp(sessionId,questionId));}
 @PostMapping("/{sessionId}/finish") public ApiResponse<AiInterviewSessionResponse> finish(@PathVariable Long sessionId){return ApiResponse.ok(service.finish(sessionId));}
}
