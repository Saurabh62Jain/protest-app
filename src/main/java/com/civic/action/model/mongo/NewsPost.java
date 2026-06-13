package com.civic.action.model.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import lombok.Data;
import java.time.Instant;
import java.util.*;

@Document(collection = "news_posts")
@Data
public class NewsPost {

    @Id
    private String id;

    @Indexed(unique = true)
    private String readableNewsId;

    @Indexed
    private Long authorId; // Reference to Postgres User.id (must NOT be citizen, e.g. APPROVER or ADMIN)
    
    private String authorName;
    private String authorDesignation;

    private String content;
    
    private Instant programDate; // Specific date/time of the event/program
    
    private List<String> photoUrls = new ArrayList<>();

    private Instant createdAt = Instant.now();

    // MongoDB TTL Index: Document will automatically delete itself at this timestamp
    @Indexed(expireAfterSeconds = 0)
    private Instant expirationDate;

    // Location target for the news (e.g. specific Ward or Constituency code)
    @Indexed
    private String locationCode;

    private int likeCount = 0;
    private Set<Long> likedByUserIds = new HashSet<>();

    private List<NewsComment> comments = new ArrayList<>();
    
    private boolean hidden = false;
    
    private boolean approved = false;

    @Data
    public static class NewsComment {
        private String id = UUID.randomUUID().toString();
        private Long userId; // Commenter User ID
        private String userName;
        private String content;
        private Instant timestamp = Instant.now();
        private int likeCount = 0;
        private Set<Long> likedByUserIds = new HashSet<>();
    }
}
