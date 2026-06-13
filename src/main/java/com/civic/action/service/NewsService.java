package com.civic.action.service;

import com.civic.action.model.mongo.NewsPost;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.mongo.NewsPostRepository;
import com.civic.action.repository.postgres.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final NewsPostRepository newsPostRepository;
    private final UserRepository userRepository;
    private final ElectionModeService electionModeService;
    private final SequenceGeneratorService sequenceGeneratorService;

    public NewsPost createNewsPost(Long authorId, String content, List<String> photoUrls, Instant programDate, Instant expirationDate, String locationCode) {
        // 1. Verify author details and role restriction
        User author = userRepository.findById(authorId)
                .orElseThrow(() -> new IllegalArgumentException("Author not found"));

        if (author.getRole() == Role.ROLE_CITIZEN) {
            throw new SecurityException("Citizens are restricted from posting program news bulletins.");
        }

        // 2. Election Mode Restrictions: If election mode is active, warn/modify behavior if needed
        if (electionModeService.isElectionModeActive()) {
            log.info("Election Mode is ACTIVE: logging news post creation for auditing.");
            // We can optionally add more strict rules here (e.g. content check)
        }

        NewsPost newsPost = new NewsPost();
        long seq = sequenceGeneratorService.generateSequence("news_sequence");
        newsPost.setReadableNewsId(String.format("NEWS-%06d", seq));
        newsPost.setAuthorId(author.getId());
        newsPost.setAuthorName(author.getMobileNumber()); // In production, name would be pulled
        newsPost.setAuthorDesignation(author.getRole() == Role.ROLE_ADMIN ? "Administrator" : author.getDesignation());
        newsPost.setContent(content);
        newsPost.setPhotoUrls(photoUrls);
        newsPost.setProgramDate(programDate);
        newsPost.setExpirationDate(expirationDate);
        newsPost.setLocationCode(locationCode);
        newsPost.setApproved(false);

        NewsPost savedPost = newsPostRepository.save(newsPost);
        log.info("Created ephemeral news post expiring at: {}", savedPost.getExpirationDate());
        return savedPost;
    }

    public List<NewsPost> getNewsBulletinForLocation(String locationCode) {
        return newsPostRepository.findByLocationCodeAndApprovedTrueAndHiddenFalseOrderByCreatedAtDesc(locationCode);
    }

    public NewsPost toggleLike(String newsPostId, Long userId) {
        NewsPost post = newsPostRepository.findById(newsPostId)
                .orElseThrow(() -> new IllegalArgumentException("News post not found"));

        if (post.getLikedByUserIds().contains(userId)) {
            post.getLikedByUserIds().remove(userId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            post.getLikedByUserIds().add(userId);
            post.setLikeCount(post.getLikeCount() + 1);
        }
        return newsPostRepository.save(post);
    }

    public NewsPost addComment(String newsPostId, Long userId, String content) {
        NewsPost post = newsPostRepository.findById(newsPostId)
                .orElseThrow(() -> new IllegalArgumentException("News post not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost.NewsComment comment = new NewsPost.NewsComment();
        comment.setUserId(userId);
        
        String name = user.getName() != null && !user.getName().isBlank() ? user.getName() : "Citizen";
        if (user.getRole() == Role.ROLE_APPROVER && user.getDesignation() != null) {
            name = user.getDesignation();
        } else if (user.getRole() == Role.ROLE_ADMIN) {
            name = "Admin";
        }
        comment.setUserName(name);
        comment.setContent(content);

        post.getComments().add(comment);
        return newsPostRepository.save(post);
    }

    public NewsPost toggleCommentLike(String newsPostId, String commentId, Long userId) {
        NewsPost post = newsPostRepository.findById(newsPostId)
                .orElseThrow(() -> new IllegalArgumentException("News post not found"));

        post.getComments().stream()
                .filter(c -> c.getId().equals(commentId))
                .findFirst()
                .ifPresent(c -> {
                    if (c.getLikedByUserIds().contains(userId)) {
                        c.getLikedByUserIds().remove(userId);
                        c.setLikeCount(Math.max(0, c.getLikeCount() - 1));
                    } else {
                        c.getLikedByUserIds().add(userId);
                        c.setLikeCount(c.getLikeCount() + 1);
                    }
                });

        return newsPostRepository.save(post);
    }

    public void deleteNewsPost(String newsPostId) {
        newsPostRepository.deleteById(newsPostId);
    }

    public void toggleHide(String newsPostId, boolean hidden) {
        newsPostRepository.findById(newsPostId).ifPresent(post -> {
            post.setHidden(hidden);
            newsPostRepository.save(post);
        });
    }

    public List<NewsPost> getPendingNewsPosts() {
        return newsPostRepository.findByApprovedFalse();
    }

    public NewsPost approveNewsPost(String id, Long approverId) {
        User user = userRepository.findById(approverId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getRole() != Role.ROLE_ADMIN && user.getRole() != Role.ROLE_APPROVER) {
            throw new SecurityException("Only administrators or designated approvers can approve news bulletins.");
        }

        NewsPost post = newsPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("News post not found"));

        post.setApproved(true);
        return newsPostRepository.save(post);
    }
}
