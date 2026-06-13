package com.civic.action.service;

import com.civic.action.model.mongo.Survey;
import com.civic.action.model.mongo.SurveyResponse;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.mongo.SurveyRepository;
import com.civic.action.repository.mongo.SurveyResponseRepository;
import com.civic.action.repository.postgres.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final UserRepository userRepository;
    private final SequenceGeneratorService sequenceGeneratorService;

    public Survey createSurvey(Long creatorId, String title, String description, List<Survey.SurveyQuestion> questions, String targetBoundaryCode, Instant expirationDate) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Creator not found"));

        Survey survey = new Survey();
        long seq = sequenceGeneratorService.generateSequence("survey_sequence");
        survey.setReadableSurveyId(String.format("SURVEY-%06d", seq));
        survey.setCreatorId(creatorId);
        survey.setCreatorName(creator.getMobileNumber()); // In real app, pull name
        survey.setTitle(title);
        survey.setDescription(description);
        survey.setQuestions(questions);
        survey.setTargetBoundaryCode(targetBoundaryCode);
        survey.setExpirationDate(expirationDate);
        survey.setApproved(false); // Survey requires Admin approval before going live

        Survey savedSurvey = surveyRepository.save(survey);
        log.info("Survey '{}' created by user {} targeting {}. Pending admin approval.", 
                title, creatorId, targetBoundaryCode);
        return savedSurvey;
    }

    public Survey approveSurvey(String id, Long approverId) {
        User user = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.ROLE_ADMIN && user.getRole() != Role.ROLE_APPROVER) {
            throw new SecurityException("Only administrators or designated approvers can approve surveys to go live.");
        }

        Survey survey = surveyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        survey.setApproved(true);
        survey.setApprovedByAdminId(approverId);
        
        log.info("Survey '{}' ({}) approved by User: {}", survey.getTitle(), id, approverId);
        return surveyRepository.save(survey);
    }

    public List<Survey> getActiveSurveysForLocation(String boundaryCode) {
        // Return only approved surveys that target the boundary and are not expired
        List<Survey> surveys = surveyRepository.findByTargetBoundaryCodeAndApprovedTrue(boundaryCode);
        return surveys.stream()
                .filter(s -> s.getExpirationDate() == null || s.getExpirationDate().isAfter(Instant.now()))
                .toList();
    }

    public List<Survey> getPendingSurveys() {
        return surveyRepository.findByApprovedFalse();
    }

    public SurveyResponse submitSurveyResponse(String surveyId, Long userId, Map<String, List<String>> answers) {
        // 1. Verify survey is approved and active
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        if (!survey.isApproved()) {
            throw new IllegalStateException("Cannot respond to an unapproved survey.");
        }

        if (survey.getExpirationDate() != null && survey.getExpirationDate().isBefore(Instant.now())) {
            throw new IllegalStateException("This survey has expired.");
        }

        // 2. Prevent duplicate responses: One response per user per survey
        if (surveyResponseRepository.existsBySurveyIdAndUserId(surveyId, userId)) {
            throw new IllegalStateException("You have already submitted a response for this survey.");
        }

        // 3. Save response
        SurveyResponse response = new SurveyResponse();
        response.setSurveyId(surveyId);
        response.setUserId(userId);
        response.setAnswers(answers);

        SurveyResponse savedResponse = surveyResponseRepository.save(response);
        log.info("User {} submitted response to survey {}", userId, surveyId);
        return savedResponse;
    }

    public long getResponseCount(String surveyId) {
        return surveyResponseRepository.countBySurveyId(surveyId);
    }
}
