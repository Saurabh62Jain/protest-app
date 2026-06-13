package com.civic.action.controller;

import com.civic.action.service.ChatbotService;
import com.civic.action.service.ChatbotService.ChatResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Data
    public static class ChatRequest {
        private String message;
        private double latitude;
        private double longitude;
        private String sessionId;
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_CITIZEN') or hasRole('ROLE_APPROVER')")
    public ResponseEntity<ChatResponse> handleChatMessage(
            @AuthenticationPrincipal String mobileNumber,
            @RequestBody ChatRequest request) {
        
        // Use default/fallback coordinate bounds if none specified
        double lat = request.getLatitude() != 0.0 ? request.getLatitude() : 23.2599;
        double lng = request.getLongitude() != 0.0 ? request.getLongitude() : 77.4126;
        
        // Generate a simple sessionId based on mobileNumber if none passed
        String sessionId = request.getSessionId() != null && !request.getSessionId().isBlank() 
                ? request.getSessionId() : mobileNumber;

        ChatResponse response = chatbotService.processChatMessage(
                sessionId,
                request.getMessage(),
                lat,
                lng,
                mobileNumber
        );

        return ResponseEntity.ok(response);
    }
}
