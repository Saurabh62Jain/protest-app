package com.civic.action.service;

import com.civic.action.model.mongo.Issue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedRankingService {

    private final MongoTemplate mongoTemplate;
    
    // Gravity constant for time decay factor
    private static final double GRAVITY = 1.5;

    /**
     * Gets a list of localized trending issues, sorted dynamically using a Hacker News-style time decay formula.
     * Math: Score = (likes + 1) / (AgeInHours + 2) ^ 1.5
     */
    public List<Issue> getTrendingIssuesForBoundary(String boundaryCode, int skip, int limit) {

        // 1. MATCH: Filter issues belonging to the targeted boundary, are APPROVED, and not hidden
        MatchOperation matchStage = Aggregation.match(
                Criteria.where("status").is("APPROVED")
                        .and("hidden").is(false)
                        .orOperator(
                                Criteria.where("wardCode").is(boundaryCode),
                                Criteria.where("vidhanSabhaCode").is(boundaryCode),
                                Criteria.where("lokSabhaCode").is(boundaryCode)
                        )
        );

        // 2. PROJECT: Compute the trend scores inside MongoDB cluster using system dates arithmetic
        ProjectionOperation projectStage = Aggregation.project()
                // Retrieve all original root properties of the document
                .and(Aggregation.ROOT).as("document")
                
                // Age calculation: Convert milliseconds difference to hours
                // expression: (new Date() - createdAt) / (1000 * 60 * 60)
                .andExpression("([0] - createdAt) / 3600000", new java.util.Date()).as("ageInHours")
                
                // Score = (likeCount + 1) / ((ageInHours + 2) ^ 1.5)
                .and(ArithmeticOperators.Divide.valueOf(
                        ArithmeticOperators.Add.valueOf("likeCount").add(1)
                ).divideBy(
                        ArithmeticOperators.Pow.valueOf(
                                ArithmeticOperators.Add.valueOf("ageInHours").add(2)
                        ).pow(GRAVITY)
                )).as("trendingScore");

        // 3. SORT: Sort descending by calculated score
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "trendingScore");

        // 4. PAGINATE: Limit and offset ranges
        SkipOperation skipStage = Aggregation.skip((long) skip);
        LimitOperation limitStage = Aggregation.limit(limit);

        // 5. REPLACE ROOT: Map outputs back to cleaner Issue models
        ReplaceRootOperation replaceRootStage = Aggregation.replaceRoot("document");

        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                projectStage,
                sortStage,
                skipStage,
                limitStage,
                replaceRootStage
        );

        AggregationResults<Issue> results = mongoTemplate.aggregate(aggregation, "issues", Issue.class);
        return results.getMappedResults();
    }
}
