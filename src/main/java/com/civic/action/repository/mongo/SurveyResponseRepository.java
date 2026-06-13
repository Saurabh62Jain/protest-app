package com.civic.action.repository.mongo;

import com.civic.action.model.mongo.SurveyResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyResponseRepository extends MongoRepository<SurveyResponse, String> {
    Optional<SurveyResponse> findBySurveyIdAndUserId(String surveyId, Long userId);
    List<SurveyResponse> findBySurveyId(String surveyId);
    boolean existsBySurveyIdAndUserId(String surveyId, Long userId);
    long countBySurveyId(String surveyId);
}
