package com.civic.action.service;

import com.civic.action.model.mongo.Survey;
import com.civic.action.model.mongo.SurveyResponse;
import com.civic.action.repository.mongo.SurveyRepository;
import com.civic.action.repository.mongo.SurveyResponseRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LobbyingAnalyticsService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    @Data
    public static class LocalizedSentimentReport {
        private String surveyId;
        private String surveyTitle;
        private String targetBoundaryCode;
        private long totalResponses;
        // questionId -> (option -> count)
        private Map<String, Map<String, Long>> aggregatedAnswers = new HashMap<>();
    }

    /**
     * Aggregates survey response choices to compile localized public sentiment analytics.
     * Securely anonymizes individual user inputs.
     */
    public LocalizedSentimentReport compileSentimentReport(String surveyId) {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        List<SurveyResponse> responses = surveyResponseRepository.findBySurveyId(surveyId);

        LocalizedSentimentReport report = new LocalizedSentimentReport();
        report.setSurveyId(surveyId);
        report.setSurveyTitle(survey.getTitle());
        report.setTargetBoundaryCode(survey.getTargetBoundaryCode());
        report.setTotalResponses(responses.size());

        // Initialize question aggregation structures
        for (Survey.SurveyQuestion q : survey.getQuestions()) {
            Map<String, Long> optionCounts = new HashMap<>();
            for (String option : q.getOptions()) {
                optionCounts.put(option, 0L);
            }
            report.getAggregatedAnswers().put(q.getQuestionText(), optionCounts);
        }

        // Aggregate responses anonymously
        for (SurveyResponse resp : responses) {
            for (Map.Entry<String, List<String>> entry : resp.getAnswers().entrySet()) {
                String questionId = entry.getKey();
                List<String> selectedOptions = entry.getValue();

                // Find matching question text to make report readable
                String questionText = survey.getQuestions().stream()
                        .filter(q -> q.getId().equals(questionId))
                        .map(Survey.SurveyQuestion::getQuestionText)
                        .findFirst()
                        .orElse("Question_" + questionId);

                Map<String, Long> counts = report.getAggregatedAnswers().computeIfAbsent(questionText, k -> new HashMap<>());

                for (String option : selectedOptions) {
                    counts.put(option, counts.getOrDefault(option, 0L) + 1);
                }
            }
        }

        log.info("Compiled sentiment report for survey {} with {} responses", surveyId, responses.size());
        return report;
    }
}
