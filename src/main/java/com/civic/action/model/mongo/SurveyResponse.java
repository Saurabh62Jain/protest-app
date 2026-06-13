package com.civic.action.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Document(collection = "survey_responses")
@Data
public class SurveyResponse {

    @Id
    private String id;

    @Indexed
    private String surveyId;

    @Indexed
    private Long userId; // Respondent User.id

    private Map<String, List<String>> answers = new HashMap<>(); // questionId -> selected options

    private Instant submittedAt = Instant.now();
}
