package com.civic.action.repository.mongo;

import com.civic.action.model.mongo.Survey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyRepository extends MongoRepository<Survey, String> {
    java.util.Optional<Survey> findByReadableSurveyId(String readableSurveyId);
    List<Survey> findByTargetBoundaryCodeAndApprovedTrue(String targetBoundaryCode);
    List<Survey> findByApprovedFalse();
}
