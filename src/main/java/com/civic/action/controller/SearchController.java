package com.civic.action.controller;

import com.civic.action.model.mongo.Issue;
import com.civic.action.model.mongo.Survey;
import com.civic.action.model.mongo.NewsPost;
import com.civic.action.repository.mongo.IssueRepository;
import com.civic.action.repository.mongo.SurveyRepository;
import com.civic.action.repository.mongo.NewsPostRepository;
import com.civic.action.util.MaskingUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final IssueRepository issueRepository;
    private final SurveyRepository surveyRepository;
    private final NewsPostRepository newsPostRepository;

    @Data
    public static class SearchResult {
        private String type; // "ISSUE", "SURVEY", "NEWS"
        private Object data;
    }

    @GetMapping
    public ResponseEntity<SearchResult> search(@RequestParam String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        
        String cleanQuery = query.trim().toUpperCase();
        SearchResult result = new SearchResult();

        if (cleanQuery.startsWith("ISSUE-")) {
            Issue issue = issueRepository.findByReadableIssueId(cleanQuery).orElse(null);
            if (issue != null) {
                result.setType("ISSUE");
                result.setData(sanitize(issue));
                return ResponseEntity.ok(result);
            }
        } else if (cleanQuery.startsWith("SURVEY-")) {
            Survey survey = surveyRepository.findByReadableSurveyId(cleanQuery).orElse(null);
            if (survey != null) {
                result.setType("SURVEY");
                result.setData(sanitize(survey));
                return ResponseEntity.ok(result);
            }
        } else if (cleanQuery.startsWith("NEWS-")) {
            NewsPost post = newsPostRepository.findByReadableNewsId(cleanQuery).orElse(null);
            if (post != null) {
                result.setType("NEWS");
                result.setData(sanitize(post));
                return ResponseEntity.ok(result);
            }
        }

        return ResponseEntity.notFound().build();
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

    private Survey sanitize(Survey survey) {
        if (survey == null) return null;
        if (survey.getCreatorName() != null) {
            survey.setCreatorName(MaskingUtil.maskMobileNumber(survey.getCreatorName()));
        }
        return survey;
    }

    private NewsPost sanitize(NewsPost post) {
        if (post == null) return null;
        if (post.getAuthorName() != null) {
            post.setAuthorName(MaskingUtil.maskMobileNumber(post.getAuthorName()));
        }
        if (post.getComments() != null) {
            for (NewsPost.NewsComment comment : post.getComments()) {
                if (comment.getUserName() != null) {
                    comment.setUserName(MaskingUtil.maskMobileNumber(comment.getUserName()));
                }
            }
        }
        return post;
    }
}
