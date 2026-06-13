package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.mongo.NewsPost;
import com.civic.action.model.mongo.Survey;
import com.civic.action.repository.mongo.IssueRepository;
import com.civic.action.repository.mongo.NewsPostRepository;
import com.civic.action.repository.mongo.SurveyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class DbPrintTest {

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private SurveyRepository surveyRepository;

    @Autowired
    private NewsPostRepository newsPostRepository;

    @Autowired
    private FeedRankingService feedRankingService;

    @Test
    public void printDatabaseContents() {
        System.out.println("==================== MONGO DB CONTENTS ====================");
        
        List<Issue> issues = issueRepository.findAll();
        System.out.println("Total Issues: " + issues.size());
        for (Issue issue : issues) {
            System.out.println("Issue ID: " + issue.getId());
            System.out.println("  Readable ID: " + issue.getReadableIssueId());
            System.out.println("  Title: " + issue.getTitle());
            System.out.println("  Status: " + issue.getStatus());
            System.out.println("  Ward Code: " + issue.getWardCode());
            System.out.println("  Approval Checklist: " + issue.getApprovalChecklist());
            System.out.println("  Hidden: " + issue.isHidden());
        }

        List<Survey> surveys = surveyRepository.findAll();
        System.out.println("Total Surveys: " + surveys.size());
        for (Survey survey : surveys) {
            System.out.println("Survey ID: " + survey.getId());
            System.out.println("  Title: " + survey.getTitle());
            System.out.println("  Approved: " + survey.isApproved());
            System.out.println("  Target Boundary Code: " + survey.getTargetBoundaryCode());
        }

        List<NewsPost> newsPosts = newsPostRepository.findAll();
        System.out.println("Total News Posts: " + newsPosts.size());
        for (NewsPost post : newsPosts) {
            System.out.println("News ID: " + post.getId());
            System.out.println("  Content: " + post.getContent());
            System.out.println("  Location Code: " + post.getLocationCode());
        }
        
        System.out.println("--- Testing Feed Ranking Service query ---");
        try {
            List<Issue> trending = feedRankingService.getTrendingIssuesForBoundary("WARD-01", 0, 50);
            System.out.println("Trending issues returned for WARD-01: " + trending.size());
            for (Issue issue : trending) {
                System.out.println("  Trending ID: " + issue.getId() + ", Title: " + issue.getTitle() + ", Status: " + issue.getStatus());
            }
        } catch (Exception e) {
            System.out.println("Feed ranking service query failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("===========================================================");
    }
}
