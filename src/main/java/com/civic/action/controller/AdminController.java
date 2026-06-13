package com.civic.action.controller;

import com.civic.action.model.mongo.Survey;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import com.civic.action.service.ElectionModeService;
import com.civic.action.service.IssueWorkflowService;
import com.civic.action.service.NewsService;
import com.civic.action.service.SurveyService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final SurveyService surveyService;
    private final ElectionModeService electionModeService;
    private final IssueWorkflowService issueWorkflowService;
    private final NewsService newsService;
    private final UserRepository userRepository;

    @Data
    public static class ElectionModeToggleRequest {
        private boolean active;
    }

    @PostMapping("/surveys/{id}/approve")
    public ResponseEntity<Survey> approveSurvey(
            @AuthenticationPrincipal String adminMobile,
            @PathVariable String id) {
        User admin = userRepository.findByMobileNumber(adminMobile)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));

        Survey survey = surveyService.approveSurvey(id, admin.getId());
        return ResponseEntity.ok(survey);
    }

    @GetMapping("/surveys/pending")
    public ResponseEntity<List<Survey>> getPendingSurveys() {
        return ResponseEntity.ok(surveyService.getPendingSurveys());
    }

    @PostMapping("/config/election-mode")
    public ResponseEntity<String> toggleElectionMode(@RequestBody ElectionModeToggleRequest request) {
        electionModeService.setElectionMode(request.isActive());
        return ResponseEntity.ok("Election Mode configuration state set to: " + request.isActive());
    }

    @PostMapping("/users/{userId}/shadowban")
    public ResponseEntity<String> shadowbanUser(
            @PathVariable Long userId,
            @RequestParam boolean shadowbanned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setShadowbanned(shadowbanned);
        userRepository.save(user);
        
        String action = shadowbanned ? "shadowbanned" : "un-shadowbanned";
        return ResponseEntity.ok("User ID " + userId + " has been successfully " + action + ".");
    }

    @PostMapping("/issues/{id}/toggle-hide")
    public ResponseEntity<String> toggleIssueVisibility(
            @PathVariable String id,
            @RequestParam boolean hidden) {
        issueWorkflowService.toggleHide(id, hidden);
        String state = hidden ? "hidden" : "visible";
        return ResponseEntity.ok("Issue visibility updated to: " + state);
    }

    @PostMapping("/news/{id}/toggle-hide")
    public ResponseEntity<String> toggleNewsPostVisibility(
            @PathVariable String id,
            @RequestParam boolean hidden) {
        newsService.toggleHide(id, hidden);
        String state = hidden ? "hidden" : "visible";
        return ResponseEntity.ok("News post visibility updated to: " + state);
    }
}
