package com.civic.action.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Document(collection = "surveys")
@Data
public class Survey {

    @Id
    private String id;

    @Indexed(unique = true)
    private String readableSurveyId;

    private Long creatorId; // Reference to Postgres User.id (corporation, media house, or politician)
    
    private String creatorName;

    private String title;
    
    private String description;

    private List<SurveyQuestion> questions = new ArrayList<>();

    @Indexed
    private String targetBoundaryCode; // Targeted geo-fenced region code

    @Indexed
    private boolean approved = false; // Requires Admin approval to go live

    private Long approvedByAdminId;
    
    private Instant createdAt = Instant.now();
    
    private Instant expirationDate;

    @Data
    public static class SurveyQuestion {
        private String id = UUID.randomUUID().toString();
        private String questionText;
        private List<String> options = new ArrayList<>(); // MCQ choices
        private boolean multiSelect = false;
    }
}
