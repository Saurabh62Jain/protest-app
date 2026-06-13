package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FeedRankingServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private FeedRankingService feedRankingService;

    @Test
    public void testGetTrendingIssuesForBoundarySuccess() {
        String boundaryCode = "WARD-12";
        int skip = 0;
        int limit = 10;

        List<Issue> mockIssues = new ArrayList<>();
        Issue issue = new Issue();
        issue.setReadableIssueId("ISSUE-000001");
        issue.setWardCode(boundaryCode);
        mockIssues.add(issue);

        @SuppressWarnings("unchecked")
        AggregationResults<Issue> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(mockIssues);

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("issues"), eq(Issue.class)))
                .thenReturn(mockResults);

        List<Issue> result = feedRankingService.getTrendingIssuesForBoundary(boundaryCode, skip, limit);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ISSUE-000001", result.get(0).getReadableIssueId());
        
        verify(mongoTemplate, times(1)).aggregate(any(Aggregation.class), eq("issues"), eq(Issue.class));
    }
}
