package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.postgres.GeoBoundary;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.mongo.IssueRepository;
import com.civic.action.repository.postgres.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IssueWorkflowServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpatialResolutionService spatialResolutionService;

    @Mock
    private SequenceGeneratorService sequenceGeneratorService;

    @InjectMocks
    private IssueWorkflowService issueWorkflowService;

    @Test
    public void testCreateIssueSuccess() {
        Long creatorId = 1L;
        User creator = new User();
        creator.setId(creatorId);
        creator.setMobileNumber("+919876543210");
        creator.setVoterId("VOTER12345");
        creator.setRole(Role.ROLE_CITIZEN);

        GeoBoundary ward = new GeoBoundary();
        ward.setCode("WARD-01");
        Map<String, GeoBoundary> politicalHierarchy = new HashMap<>();
        politicalHierarchy.put("ward", ward);

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(spatialResolutionService.resolvePoliticalHierarchy(12.34, 56.78)).thenReturn(politicalHierarchy);
        when(sequenceGeneratorService.generateSequence("issue_sequence")).thenReturn(101L);
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Issue issue = issueWorkflowService.createIssue(creatorId, "Water Leak", "Leaking pipe", List.of(), 12.34, 56.78);

        assertNotNull(issue);
        assertEquals("ISSUE-000101", issue.getReadableIssueId());
        assertEquals(creatorId, issue.getCreatorId());
        assertEquals("WARD-01", issue.getWardCode());
        assertEquals("SUBMITTED", issue.getStatus());
        assertTrue(issue.getApprovalChecklist().get("VOTER_ID_VERIFIED"));
        assertFalse(issue.getApprovalChecklist().get("CONTENT_APPROPRIATE"));

        verify(userRepository, times(1)).findById(creatorId);
        verify(spatialResolutionService, times(1)).resolvePoliticalHierarchy(12.34, 56.78);
        verify(sequenceGeneratorService, times(1)).generateSequence("issue_sequence");
        verify(issueRepository, times(1)).save(any(Issue.class));
    }

    @Test
    public void testCreateIssueNoVoterIdThrowsException() {
        Long creatorId = 1L;
        User creator = new User();
        creator.setId(creatorId);
        creator.setMobileNumber("+919876543210");
        creator.setRole(Role.ROLE_CITIZEN); // Voter ID is null

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));

        assertThrows(IllegalStateException.class, () -> {
            issueWorkflowService.createIssue(creatorId, "Water Leak", "Leaking pipe", List.of(), 12.34, 56.78);
        });

        verify(userRepository, times(1)).findById(creatorId);
        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    public void testApproveIssueSuccess() {
        String issueId = "mongo-id-123";
        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setReadableIssueId("ISSUE-000101");
        issue.getApprovalChecklist().put("VOTER_ID_VERIFIED", true);
        issue.getApprovalChecklist().put("LOCATION_VERIFIED", true);
        issue.getApprovalChecklist().put("CONTENT_APPROPRIATE", true);
        issue.getApprovalChecklist().put("DUPLICATE_CHECK_PASSED", true);

        Long approverId = 2L;
        User approver = new User();
        approver.setId(approverId);
        approver.setMobileNumber("+919999999999");
        approver.setRole(Role.ROLE_APPROVER);
        approver.setDesignation("Ward Councilor");

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Issue approved = issueWorkflowService.approveIssue(issueId, approverId);

        assertEquals("APPROVED", approved.getStatus());
        assertEquals(approverId, approved.getApprovedById());
        assertEquals("Ward Councilor", approved.getApprovedByDesignation());

        verify(issueRepository, times(1)).findById(issueId);
        verify(userRepository, times(1)).findById(approverId);
        verify(issueRepository, times(1)).save(approved);
    }

    @Test
    public void testApproveIssueMissingChecklistThrowsException() {
        String issueId = "mongo-id-123";
        Issue issue = new Issue();
        issue.setId(issueId);
        issue.getApprovalChecklist().put("VOTER_ID_VERIFIED", true);
        issue.getApprovalChecklist().put("LOCATION_VERIFIED", true);
        issue.getApprovalChecklist().put("CONTENT_APPROPRIATE", false); // Not verified yet

        Long approverId = 2L;
        User approver = new User();
        approver.setId(approverId);
        approver.setRole(Role.ROLE_APPROVER);

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(userRepository.findById(approverId)).thenReturn(Optional.of(approver));

        assertThrows(IllegalStateException.class, () -> {
            issueWorkflowService.approveIssue(issueId, approverId);
        });

        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    public void testCloseIssueSuccess() {
        String issueId = "mongo-id-123";
        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setCreatorId(1L);
        issue.setStatus("RESOLVED");

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Issue closed = issueWorkflowService.closeIssue(issueId, 1L);

        assertEquals("CLOSED", closed.getStatus());
        verify(issueRepository, times(1)).save(closed);
    }

    @Test
    public void testCloseIssueNotCreatorThrowsException() {
        String issueId = "mongo-id-123";
        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setCreatorId(1L);
        issue.setStatus("RESOLVED");

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));

        assertThrows(SecurityException.class, () -> {
            issueWorkflowService.closeIssue(issueId, 99L); // Wrong user id
        });

        verify(issueRepository, never()).save(any(Issue.class));
    }

    @Test
    public void testToggleLike() {
        String issueId = "mongo-id-123";
        Issue issue = new Issue();
        issue.setId(issueId);
        issue.setLikeCount(0);
        issue.setLikedByUserIds(new HashSet<>());

        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Like first time
        Issue liked = issueWorkflowService.toggleLike(issueId, 5L);
        assertEquals(1, liked.getLikeCount());
        assertTrue(liked.getLikedByUserIds().contains(5L));

        // Unlike second time
        Issue unliked = issueWorkflowService.toggleLike(issueId, 5L);
        assertEquals(0, unliked.getLikeCount());
        assertFalse(unliked.getLikedByUserIds().contains(5L));
    }
}
