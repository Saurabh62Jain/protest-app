package com.civic.action.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Document(collection = "issues")
@Data
public class Issue {

    @Id
    private String id; // MongoDB ObjectId

    @Indexed(unique = true)
    private String readableIssueId; // E.g., ISSUE-10001, ISSUE-10002

    @Indexed
    private Long creatorId; // Reference to Postgres User.id

    private String creatorMobile;
    
    private String title;
    
    private String description;
    
    private List<String> photoUrls = new ArrayList<>();

    // Geospatial Index in MongoDB for proximity searches
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint issueLocation;

    // Boundary mappings populated from PostGIS
    @Indexed
    private String wardCode;
    
    @Indexed
    private String vidhanSabhaCode;
    
    @Indexed
    private String lokSabhaCode;

    // SUBMITTED, UNDER_REVIEW, APPROVED, RESOLVED, CLOSED
    @Indexed
    private String status = "SUBMITTED";

    private int likeCount = 0;
    
    private Set<Long> likedByUserIds = new HashSet<>();

    private Instant createdAt = Instant.now();
    
    private Instant updatedAt = Instant.now();

    // Checklist of issues checked before approval
    private Map<String, Boolean> approvalChecklist = new HashMap<>();

    // Details of the Approver
    private Long approvedById;
    private String approvedByName;
    private String approvedByDesignation;

    // Chat thread between Approver and Creator for resolving issue descriptions
    private List<IssueMessage> messages = new ArrayList<>();

    // Public community comments
    private List<IssueComment> comments = new ArrayList<>();

    // Reporting log for flag moderation
    private List<ReportLog> reports = new ArrayList<>();
    
    private boolean hidden = false;

    @Data
    public static class IssueMessage {
        private String id = UUID.randomUUID().toString();
        private Long senderId; // User ID of sender
        private String senderName;
        private String content;
        private Instant timestamp = Instant.now();
    }

    @Data
    public static class IssueComment {
        private String id = UUID.randomUUID().toString();
        private Long userId;
        private String userName;
        private String content;
        private Instant timestamp = Instant.now();
    }

    @Data
    public static class ReportLog {
        private String id = UUID.randomUUID().toString();
        private Long reportedById;
        private String reason;
        private Instant timestamp = Instant.now();
    }
}
