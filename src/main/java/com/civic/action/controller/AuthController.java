package com.civic.action.controller;

import com.civic.action.dto.request.OtpRequest;
import com.civic.action.dto.request.OtpVerifyRequest;
import com.civic.action.dto.request.VoterLinkRequest;
import com.civic.action.dto.response.AuthResponse;
import com.civic.action.service.AuthService;
import com.civic.action.service.GoogleAuthService;
import com.civic.action.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final AuthService authService;
    private final GoogleAuthService googleAuthService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> verifyGoogleLogin(
            @RequestHeader("X-Google-Client-ID") String clientId,
            @RequestBody String idToken) {
        AuthResponse response = googleAuthService.verifyGoogleTokenAndLogin(idToken, clientId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/otp/request")
    public ResponseEntity<String> requestOtp(@Valid @RequestBody OtpRequest request) {
        otpService.generateAndSendOtp(request.getMobileNumber());
        return ResponseEntity.ok("OTP sent successfully to " + request.getMobileNumber());
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        AuthResponse response = authService.verifyOtpAndLogin(request.getMobileNumber(), request.getOtpCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/voter-id/link")
    public ResponseEntity<String> linkVoterId(
            @AuthenticationPrincipal String mobileNumber,
            @Valid @RequestBody VoterLinkRequest request) {
        authService.linkVoterId(mobileNumber, request.getVoterId());
        return ResponseEntity.ok("Voter ID linked successfully.");
    }
}
