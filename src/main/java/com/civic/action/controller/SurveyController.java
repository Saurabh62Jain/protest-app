package com.civic.action.controller;

import com.civic.action.model.mongo.Survey;
import com.civic.action.model.mongo.SurveyResponse;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import com.civic.action.service.LobbyingAnalyticsService;
import com.civic.action.service.SurveyService;
import com.civic.action.service.SpatialResolutionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.civic.action.util.MaskingUtil;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final LobbyingAnalyticsService lobbyingAnalyticsService;
    private final UserRepository userRepository;
    private final SpatialResolutionService spatialResolutionService;

    @Data
    public static class SurveyCreateRequest {
        private String title;
        private String description;
        private List<Survey.SurveyQuestion> questions;
        private String targetBoundaryCode;
        private Instant expirationDate;
    }

    @Data
    public static class ResponseSubmitRequest {
        private Map<String, List<String>> answers; // questionId -> selected options
    }

    @PostMapping
    public ResponseEntity<Survey> createSurvey(
            @AuthenticationPrincipal String mobileNumber,
            @RequestBody SurveyCreateRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Survey survey = surveyService.createSurvey(
                user.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getQuestions(),
                request.getTargetBoundaryCode(),
                request.getExpirationDate()
        );
        return ResponseEntity.ok(sanitize(survey));
    }

    @GetMapping("/active")
    public ResponseEntity<List<Survey>> getActiveSurveys(
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        
        String resolvedCode = locationCode;
        if ((resolvedCode == null || resolvedCode.isEmpty()) && latitude != null && longitude != null) {
            var boundaries = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
            if (boundaries.containsKey("ward")) {
                resolvedCode = boundaries.get("ward").getCode();
            } else if (boundaries.containsKey("vidhanSabha")) {
                resolvedCode = boundaries.get("vidhanSabha").getCode();
            } else if (boundaries.containsKey("lokSabha")) {
                resolvedCode = boundaries.get("lokSabha").getCode();
            } else {
                resolvedCode = "WARD-01";
            }
        }
        
        if (resolvedCode == null || resolvedCode.isEmpty() || "GLOBAL".equals(resolvedCode)) {
            resolvedCode = "WARD-01"; // Fallback default
        }
        
        List<Survey> surveys = surveyService.getActiveSurveysForLocation(resolvedCode);
        return ResponseEntity.ok(sanitize(surveys));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<SurveyResponse> respondToSurvey(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @RequestBody ResponseSubmitRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SurveyResponse response = surveyService.submitSurveyResponse(id, user.getId(), request.getAnswers());
        return ResponseEntity.ok(response);
    }

    // Secure Data-Driven Lobbying and Media Aggregation Export Endpoint
    @GetMapping("/{id}/lobbying-report")
    public ResponseEntity<LobbyingAnalyticsService.LocalizedSentimentReport> getSentimentReport(@PathVariable String id) {
        LobbyingAnalyticsService.LocalizedSentimentReport report = lobbyingAnalyticsService.compileSentimentReport(id);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/approver/pending")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public ResponseEntity<List<Survey>> getPendingSurveys(
            @AuthenticationPrincipal String mobileNumber) {
        List<Survey> pending = surveyService.getPendingSurveys();
        return ResponseEntity.ok(sanitize(pending));
    }

    @PostMapping("/approver/{id}/approve")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public ResponseEntity<Survey> approveSurvey(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Survey survey = surveyService.approveSurvey(id, user.getId());
        return ResponseEntity.ok(sanitize(survey));
    }

    private Survey sanitize(Survey survey) {
        if (survey == null) return null;
        if (survey.getCreatorName() != null) {
            survey.setCreatorName(MaskingUtil.maskMobileNumber(survey.getCreatorName()));
        }
        return survey;
    }

    private List<Survey> sanitize(List<Survey> surveys) {
        if (surveys == null) return null;
        for (Survey s : surveys) {
            sanitize(s);
        }
        return surveys;
    }
}
