package com.civic.action.repository.mongo;

import com.civic.action.model.mongo.NewsPost;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewsPostRepository extends MongoRepository<NewsPost, String> {
    java.util.Optional<NewsPost> findByReadableNewsId(String readableNewsId);
    List<NewsPost> findByLocationCodeAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(String locationCode);
    List<NewsPost> findByLocationCodeAndHiddenFalseOrderByCreatedAtDesc(String locationCode);
    List<NewsPost> findByAuthorIdAndHiddenFalseOrderByCreatedAtDesc(Long authorId);
    List<NewsPost> findByApprovedFalse();
}
