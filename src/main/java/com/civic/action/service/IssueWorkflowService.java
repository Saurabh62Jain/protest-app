package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.mongo.IssueRepository;
import com.civic.action.repository.postgres.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueWorkflowService {

    private final IssueRepository issueRepository;
    private final UserRepository userRepository;
    private final SpatialResolutionService spatialResolutionService;
    private final SequenceGeneratorService sequenceGeneratorService;

    @Transactional(readOnly = true)
    public Issue createIssue(Long creatorId, String title, String description, List<String> photoUrls, double latitude, double longitude) {
        // 1. Fetch Creator and validate Voter ID
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (creator.getVoterId() == null || creator.getVoterId().isBlank()) {
            throw new IllegalStateException("A verified Voter ID is required to raise an issue.");
        }

        // 2. Resolve spatial boundaries from coordinates
        Map<String, GeoBoundary> politicalHierarchy = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
        
        GeoBoundary ward = politicalHierarchy.get("ward");
        GeoBoundary vidhanSabha = politicalHierarchy.get("vidhanSabha");
        GeoBoundary lokSabha = politicalHierarchy.get("lokSabha");

        // 3. Create and populate Issue document
        Issue issue = new Issue();
        long seq = sequenceGeneratorService.generateSequence("issue_sequence");
        issue.setReadableIssueId(String.format("ISSUE-%06d", seq));
        issue.setCreatorId(creator.getId());
        issue.setCreatorMobile(creator.getMobileNumber());
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setPhotoUrls(photoUrls);
        issue.setIssueLocation(new GeoJsonPoint(longitude, latitude)); // Longitude first in GeoJson

        if (ward != null) issue.setWardCode(ward.getCode());
        if (vidhanSabha != null) issue.setVidhanSabhaCode(vidhanSabha.getCode());
        if (lokSabha != null) issue.setLokSabhaCode(lokSabha.getCode());

        // 2b. Check for duplicate issues in the same Ward
        String wardCode = ward != null ? ward.getCode() : null;
        if (wardCode != null) {
            List<Issue> activeIssues = issueRepository.findByWardCodeAndStatusAndHiddenFalse(wardCode, "SUBMITTED");
            if (activeIssues == null) {
                activeIssues = new ArrayList<>();
            }
            List<Issue> approvedIssues = issueRepository.findByWardCodeAndStatusAndHiddenFalse(wardCode, "APPROVED");
            if (approvedIssues != null) {
                activeIssues.addAll(approvedIssues);
            }

            for (Issue existing : activeIssues) {
                double similarity = calculateTextSimilarity(description, existing.getDescription());
                if (similarity >= 0.75) {
                    log.warn("Blocking duplicate issue submission in ward {}. Matches existing issue {}", wardCode, existing.getReadableIssueId());
                    throw new IllegalStateException("Duplicate Issue: A highly similar issue is already raised in your ward (" + existing.getReadableIssueId() + "). Please like or comment on that issue instead of creating a duplicate.");
                }
            }
        }

        // Initialize checklist keys
        issue.getApprovalChecklist().put("VOTER_ID_VERIFIED", true);
        issue.getApprovalChecklist().put("LOCATION_VERIFIED", true);
        issue.getApprovalChecklist().put("CONTENT_APPROPRIATE", false); // Requires manual check
        issue.getApprovalChecklist().put("DUPLICATE_CHECK_PASSED", false); // Requires manual check

        Issue savedIssue = issueRepository.save(issue);
        log.info("Created new Issue: {} for user: {}", savedIssue.getReadableIssueId(), creatorId);
        return savedIssue;
    }

    public Issue updateChecklist(String id, String checkName, boolean checked) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        issue.getApprovalChecklist().put(checkName, checked);
        issue.setUpdatedAt(Instant.now());
        return issueRepository.save(issue);
    }

    public Issue approveIssue(String id, Long approverId) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("Approver not found"));

        if (approver.getRole() != Role.ROLE_APPROVER && approver.getRole() != Role.ROLE_ADMIN) {
            throw new SecurityException("Only designated approvers can approve issues.");
        }

        // Validate checklist
        boolean allChecked = issue.getApprovalChecklist().values().stream().allMatch(val -> val);
        if (!allChecked) {
            throw new IllegalStateException("All checklist items must be verified before approval.");
        }

        issue.setStatus("APPROVED");
        issue.setApprovedById(approver.getId());
        issue.setApprovedByName(approver.getMobileNumber()); // In real app, name would be pulled
        issue.setApprovedByDesignation(approver.getDesignation());
        issue.setUpdatedAt(Instant.now());

        log.info("Issue {} approved by Approver: {}", issue.getReadableIssueId(), approverId);
        return issueRepository.save(issue);
    }

    public Issue sendMessage(String id, Long senderId, String content) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Issue.IssueMessage msg = new Issue.IssueMessage();
        msg.setSenderId(senderId);
        msg.setSenderName(sender.getRole() == Role.ROLE_APPROVER ? sender.getDesignation() : "Citizen");
        msg.setContent(content);

        issue.getMessages().add(msg);
        issue.setUpdatedAt(Instant.now());
        return issueRepository.save(issue);
    }

    public Issue resolveIssue(String id, Long userId) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        // Only creator or approved assignee can mark issue resolved
        if (!issue.getCreatorId().equals(userId) && !userId.equals(issue.getApprovedById())) {
            throw new SecurityException("Not authorized to resolve this issue.");
        }

        issue.setStatus("RESOLVED");
        issue.setUpdatedAt(Instant.now());
        log.info("Issue {} marked as RESOLVED by user {}", issue.getReadableIssueId(), userId);
        return issueRepository.save(issue);
    }

    public Issue closeIssue(String id, Long citizenId) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        // Explicit validation check: Only the citizen who raised the issue can perform final CLOSE validation
        if (!issue.getCreatorId().equals(citizenId)) {
            throw new SecurityException("Only the citizen who raised the issue can confirm resolution and close it.");
        }

        if (!"RESOLVED".equals(issue.getStatus())) {
            throw new IllegalStateException("Issue must be resolved before it can be closed.");
        }

        issue.setStatus("CLOSED");
        issue.setUpdatedAt(Instant.now());
        log.info("Issue {} marked as CLOSED and validated by creator {}", issue.getReadableIssueId(), citizenId);
        return issueRepository.save(issue);
    }

    public void reportIssue(String id, Long reporterId, String reason) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        Issue.ReportLog report = new Issue.ReportLog();
        report.setReportedById(reporterId);
        report.setReason(reason);
        issue.getReports().add(report);
        
        // Auto-hide flag if reports count exceeds a threshold (e.g. 5 reports)
        if (issue.getReports().size() >= 5) {
            issue.setHidden(true);
        }
        
        issueRepository.save(issue);
        log.info("Issue {} reported by reporter {}", issue.getReadableIssueId(), reporterId);
    }

    public Issue toggleHide(String id, boolean hidden) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));
        
        issue.setHidden(hidden);
        return issueRepository.save(issue);
    }

    public Issue toggleLike(String id, Long userId) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        if (issue.getLikedByUserIds() == null) {
            issue.setLikedByUserIds(new java.util.HashSet<>());
        }

        if (issue.getLikedByUserIds().contains(userId)) {
            issue.getLikedByUserIds().remove(userId);
            issue.setLikeCount(Math.max(0, issue.getLikeCount() - 1));
        } else {
            issue.getLikedByUserIds().add(userId);
            issue.setLikeCount(issue.getLikeCount() + 1);
        }
        return issueRepository.save(issue);
    }

    public Issue addComment(String id, Long userId, String content) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (issue.getComments() == null) {
            issue.setComments(new java.util.ArrayList<>());
        }

        Issue.IssueComment comment = new Issue.IssueComment();
        comment.setUserId(userId);
        
        String name = user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getMobileNumber();
        if (user.getRole() == Role.ROLE_APPROVER && user.getDesignation() != null) {
            name = user.getDesignation();
        } else if (user.getRole() == Role.ROLE_ADMIN) {
            name = "Admin";
        }
        comment.setUserName(name);
        comment.setContent(content);

        issue.getComments().add(comment);
        issue.setUpdatedAt(Instant.now());
        return issueRepository.save(issue);
    }

    @Transactional(readOnly = true)
    public List<Issue> getPendingIssuesForBoundary(String targetCode, Map<String, GeoBoundary> boundaries) {
        List<Issue> pending = issueRepository.findByWardCodeAndStatusAndHiddenFalse(targetCode, "SUBMITTED");
        if (pending.isEmpty() && boundaries.containsKey("vidhanSabha")) {
            pending = issueRepository.findByVidhanSabhaCodeAndStatusAndHiddenFalse(boundaries.get("vidhanSabha").getCode(), "SUBMITTED");
        }
        if (pending.isEmpty() && boundaries.containsKey("lokSabha")) {
            pending = issueRepository.findByLokSabhaCodeAndStatusAndHiddenFalse(boundaries.get("lokSabha").getCode(), "SUBMITTED");
        }
        if (pending.isEmpty()) {
            pending = issueRepository.findByStatusAndHiddenFalse("SUBMITTED");
        }
        return pending;
    }

    // Levenshtein & Jaccard Hybrid Similarity metric
    private double calculateTextSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        s1 = s1.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ").trim();
        s2 = s2.toLowerCase().replaceAll("[^a-zA-Z0-9 ]", " ").trim();

        if (s1.equals(s2)) return 1.0;

        // 1. Jaccard Index (Word Token Intersection)
        Set<String> words1 = new HashSet<>(Arrays.asList(s1.split("\\s+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.split("\\s+")));
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);
        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);
        double jaccard = (double) intersection.size() / union.size();

        // 2. Levenshtein Distance
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1])) + 1;
                }
            }
        }
        double levenshtein = (double) (Math.max(len1, len2) - dp[len1][len2]) / Math.max(len1, len2);

        // Weighted Average: 70% Jaccard (focus on terms) + 30% Levenshtein (focus on character sequence)
        return (0.7 * jaccard) + (0.3 * levenshtein);
    }
}
