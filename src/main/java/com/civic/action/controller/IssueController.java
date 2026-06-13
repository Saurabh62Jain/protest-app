package com.civic.action.controller;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import com.civic.action.repository.postgres.CandidateRepository;
import com.civic.action.service.FeedRankingService;
import com.civic.action.service.IssueWorkflowService;
import com.civic.action.service.SpatialResolutionService;
import com.civic.action.util.MaskingUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueWorkflowService issueWorkflowService;
    private final FeedRankingService feedRankingService;
    private final UserRepository userRepository;
    private final SpatialResolutionService spatialResolutionService;
    private final CandidateRepository candidateRepository;

    @Data
    public static class IssueCreateRequest {
        private String title;
        private String description;
        private List<String> photoUrls;
        private double latitude;
        private double longitude;
    }

    @Data
    public static class ChecklistUpdateRequest {
        private String checkName;
        private boolean checked;
    }

    @Data
    public static class MessageRequest {
        private String content;
    }

    @Data
    public static class ReportRequest {
        private String reason;
    }

    @PostMapping
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Map<String, Object>> raiseIssue(
            @AuthenticationPrincipal String mobileNumber,
            @RequestBody IssueCreateRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.createIssue(
                user.getId(),
                request.getTitle(),
                request.getDescription(),
                request.getPhotoUrls(),
                request.getLatitude(),
                request.getLongitude()
        );

        // Resolve candidate (politician) name for the ward
        String politicianName = "your Ward representative";
        if (issue.getWardCode() != null) {
            politicianName = candidateRepository.findByBoundaryCode(issue.getWardCode())
                    .map(c -> c.getName())
                    .orElse("your Ward representative");
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("issue", sanitize(issue));
        responseMap.put("politicianName", politicianName);

        return ResponseEntity.ok(responseMap);
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Issue> likeIssue(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        Issue issue = issueWorkflowService.toggleLike(id, user.getId());
        return ResponseEntity.ok(sanitize(issue));
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<Issue> addComment(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @RequestBody MessageRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.addComment(id, user.getId(), request.getContent());
        return ResponseEntity.ok(sanitize(issue));
    }

    @PostMapping("/{id}/message")
    public ResponseEntity<Issue> sendMessage(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @RequestBody MessageRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.sendMessage(id, user.getId(), request.getContent());
        return ResponseEntity.ok(sanitize(issue));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Issue> resolveIssue(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.resolveIssue(id, user.getId());
        return ResponseEntity.ok(sanitize(issue));
    }

    // Explicit validation endpoint: Only the citizen who raised it can confirm resolution and CLOSE the issue
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<Issue> closeIssue(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.closeIssue(id, user.getId());
        return ResponseEntity.ok(sanitize(issue));
    }

    @PostMapping("/{id}/report")
    public ResponseEntity<String> reportIssue(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @RequestBody ReportRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        issueWorkflowService.reportIssue(id, user.getId(), request.getReason());
        return ResponseEntity.ok("Post successfully reported to admin.");
    }

    @GetMapping("/feed")
    public ResponseEntity<List<Issue>> getTrendingFeed(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "20") int limit) {
        
        // Resolve ward hierarchy dynamically
        var boundaries = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
        
        // Default to ward boundary or assembly constituency
        String targetCode = "WARD-01";
        if (boundaries.containsKey("ward")) {
            targetCode = boundaries.get("ward").getCode();
        } else if (boundaries.containsKey("vidhanSabha")) {
            targetCode = boundaries.get("vidhanSabha").getCode();
        } else if (boundaries.containsKey("lokSabha")) {
            targetCode = boundaries.get("lokSabha").getCode();
        }

        List<Issue> feed = feedRankingService.getTrendingIssuesForBoundary(targetCode, skip, limit);
        return ResponseEntity.ok(sanitize(feed));
    }

    @GetMapping("/approver/pending")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<List<Issue>> getPendingIssues(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "0") int skip,
            @RequestParam(defaultValue = "100") int limit) {
        
        var boundaries = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
        
        String targetCode = "WARD-01";
        if (boundaries.containsKey("ward")) {
            targetCode = boundaries.get("ward").getCode();
        } else if (boundaries.containsKey("vidhanSabha")) {
            targetCode = boundaries.get("vidhanSabha").getCode();
        } else if (boundaries.containsKey("lokSabha")) {
            targetCode = boundaries.get("lokSabha").getCode();
        }

        List<Issue> pending = issueWorkflowService.getPendingIssuesForBoundary(targetCode, boundaries);
        return ResponseEntity.ok(sanitize(pending));
    }

    // ==========================================
    // APPROVER (POLITICIAN) ACTION ENDPOINTS
    // ==========================================

    @PostMapping("/approver/{id}/checklist")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Issue> updateChecklist(
            @PathVariable String id,
            @RequestBody ChecklistUpdateRequest request) {
        Issue issue = issueWorkflowService.updateChecklist(id, request.getCheckName(), request.isChecked());
        return ResponseEntity.ok(sanitize(issue));
    }

    @PostMapping("/approver/{id}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public ResponseEntity<Issue> approveIssue(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue issue = issueWorkflowService.approveIssue(id, user.getId());
        return ResponseEntity.ok(sanitize(issue));
     }

     @GetMapping("/resolve-location")
     public ResponseEntity<Map<String, Object>> resolveLocation(
             @RequestParam double latitude,
             @RequestParam double longitude) {
         
         var boundaries = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
         
         Map<String, Object> result = new HashMap<>();
         result.put("latitude", latitude);
         result.put("longitude", longitude);
         
         if (boundaries.containsKey("ward")) {
             var ward = boundaries.get("ward");
             result.put("wardName", ward.getName());
             result.put("wardCode", ward.getCode());
         }
         if (boundaries.containsKey("vidhanSabha")) {
             var vs = boundaries.get("vidhanSabha");
             result.put("vidhanSabhaName", vs.getName());
             result.put("vidhanSabhaCode", vs.getCode());
         }
         if (boundaries.containsKey("lokSabha")) {
             var ls = boundaries.get("lokSabha");
             result.put("lokSabhaName", ls.getName());
             result.put("lokSabhaCode", ls.getCode());
         }
         
         return ResponseEntity.ok(result);
     }

     private Issue sanitize(Issue issue) {
         if (issue == null) return null;
         if (issue.getCreatorMobile() != null) {
             issue.setCreatorMobile(MaskingUtil.maskMobileNumber(issue.getCreatorMobile()));
         }
         if (issue.getComments() != null) {
             for (Issue.IssueComment comment : issue.getComments()) {
                 if (comment.getUserName() != null) {
                     comment.setUserName(MaskingUtil.maskMobileNumber(comment.getUserName()));
                 }
             }
         }
         return issue;
     }

     private List<Issue> sanitize(List<Issue> issues) {
         if (issues == null) return null;
         for (Issue issue : issues) {
             sanitize(issue);
         }
         return issues;
     }
}
