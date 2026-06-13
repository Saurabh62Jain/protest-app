package com.civic.action.controller;

import com.civic.action.model.mongo.NewsPost;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import com.civic.action.service.NewsService;
import com.civic.action.service.SpatialResolutionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.civic.action.util.MaskingUtil;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;
    private final UserRepository userRepository;
    private final SpatialResolutionService spatialResolutionService;

    @Data
    public static class NewsCreateRequest {
        private String content;
        private List<String> photoUrls;
        private Instant programDate;
        private Instant expirationDate;
        private String locationCode;
    }

    @Data
    public static class CommentRequest {
        private String content;
    }

    @PostMapping("/approver")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public ResponseEntity<NewsPost> createNewsPost(
            @AuthenticationPrincipal String mobileNumber,
            @RequestBody NewsCreateRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost post = newsService.createNewsPost(
                user.getId(),
                request.getContent(),
                request.getPhotoUrls(),
                request.getProgramDate(),
                request.getExpirationDate(),
                request.getLocationCode()
        );
        return ResponseEntity.ok(sanitize(post));
    }

    @GetMapping("/bulletin")
    public ResponseEntity<List<NewsPost>> getLocalNewsBulletin(
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        
        String resolvedCode = locationCode;
        if ((resolvedCode == null || resolvedCode.isEmpty()) && latitude != null && longitude != null) {
            var boundaries = spatialResolutionService.resolvePoliticalHierarchy(latitude, longitude);
            if (boundaries.containsKey("ward")) {
                resolvedCode = boundaries.get("ward").getCode();
            } else if (boundaries.containsKey("vidhanSabha")) {
                resolvedCode = boundaries.get("vidhanSabha").getCode();
            } else if (boundaries.containsKey("lokSabha")) {
                resolvedCode = boundaries.get("lokSabha").getCode();
            } else {
                resolvedCode = "WARD-01";
            }
        }
        
        if (resolvedCode == null || resolvedCode.isEmpty() || "GLOBAL".equals(resolvedCode)) {
            resolvedCode = "WARD-01"; // Fallback default
        }
        
        List<NewsPost> bulletin = newsService.getNewsBulletinForLocation(resolvedCode);
        return ResponseEntity.ok(sanitize(bulletin));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<NewsPost> likeNewsPost(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost post = newsService.toggleLike(id, user.getId());
        return ResponseEntity.ok(sanitize(post));
    }

    @PostMapping("/{id}/comment")
    public ResponseEntity<NewsPost> commentOnNewsPost(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @RequestBody CommentRequest request) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost post = newsService.addComment(id, user.getId(), request.getContent());
        return ResponseEntity.ok(sanitize(post));
    }

    @PostMapping("/{id}/comment/{commentId}/like")
    public ResponseEntity<NewsPost> likeNewsComment(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id,
            @PathVariable String commentId) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost post = newsService.toggleCommentLike(id, commentId, user.getId());
        return ResponseEntity.ok(sanitize(post));
    }

    @GetMapping("/approver/pending")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public ResponseEntity<List<NewsPost>> getPendingNewsPosts(
            @AuthenticationPrincipal String mobileNumber) {
        List<NewsPost> pending = newsService.getPendingNewsPosts();
        return ResponseEntity.ok(sanitize(pending));
    }

    @PostMapping("/approver/{id}/approve")
    @PreAuthorize("hasAnyRole('APPROVER', 'ADMIN')")
    public ResponseEntity<NewsPost> approveNewsPost(
            @AuthenticationPrincipal String mobileNumber,
            @PathVariable String id) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        NewsPost post = newsService.approveNewsPost(id, user.getId());
        return ResponseEntity.ok(sanitize(post));
    }

    private NewsPost sanitize(NewsPost post) {
        if (post == null) return null;
        if (post.getAuthorName() != null) {
            post.setAuthorName(MaskingUtil.maskMobileNumber(post.getAuthorName()));
        }
        if (post.getComments() != null) {
            for (NewsPost.NewsComment comment : post.getComments()) {
                if (comment.getUserName() != null) {
                    comment.setUserName(MaskingUtil.maskMobileNumber(comment.getUserName()));
                }
            }
        }
        return post;
    }

    private List<NewsPost> sanitize(List<NewsPost> posts) {
        if (posts == null) return null;
        for (NewsPost post : posts) {
            sanitize(post);
        }
        return posts;
    }
}
