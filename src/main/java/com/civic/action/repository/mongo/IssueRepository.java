package com.civic.action.repository.mongo;

import com.civic.action.model.mongo.Issue;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IssueRepository extends MongoRepository<Issue, String> {
    Optional<Issue> findByReadableIssueId(String readableIssueId);
    List<Issue> findByCreatorId(Long creatorId);
    List<Issue> findByWardCodeAndStatusAndHiddenFalse(String wardCode, String status);
    List<Issue> findByVidhanSabhaCodeAndStatusAndHiddenFalse(String vidhanSabhaCode, String status);
    List<Issue> findByLokSabhaCodeAndStatusAndHiddenFalse(String lokSabhaCode, String status);
    List<Issue> findByStatusAndHiddenFalse(String status);
}
