package com.civic.action.service;

import com.civic.action.config.JwtService;
import com.civic.action.dto.response.AuthResponse;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse verifyGoogleTokenAndLogin(String idTokenString, String clientId) {
        try {
            // If Client ID or token is default/placeholder, do local mock validation for testing
            if (clientId == null || clientId.isBlank() || clientId.contains("placeholder") || clientId.equals("YOUR_GOOGLE_CLIENT_ID") || "mock-token-12345".equals(idTokenString)) {
                return getMockGoogleLogin(idTokenString);
            }

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(clientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new IllegalArgumentException("Invalid Google ID Token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            User user = userRepository.findByGoogleId(googleId).orElse(null);
            boolean isNewUser = false;

            if (user == null) {
                // Check if user exists by email (linked account scenario)
                user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    user = new User();
                    user.setEmail(email);
                    user.setGoogleId(googleId);
                    user.setRole(Role.ROLE_CITIZEN);
                    user.setName(name);
                    user = userRepository.save(user);
                    isNewUser = true;
                } else {
                    user.setGoogleId(googleId);
                    if (user.getName() == null || user.getName().isBlank()) {
                        user.setName(name);
                    }
                    user = userRepository.save(user);
                }
            } else {
                if (user.getName() == null || user.getName().isBlank()) {
                    user.setName(name);
                    user = userRepository.save(user);
                }
            }

            if (user.getRole() == Role.ROLE_CITIZEN && user.getVoterId() == null) {
                isNewUser = true;
            }

            // Return custom application token
            String username = user.getEmail() != null ? user.getEmail() : user.getMobileNumber();
            String token = jwtService.generateToken(username, user.getRole().name());

            AuthResponse response = new AuthResponse();
            response.setToken(token);
            response.setRole(user.getRole().name());
            response.setNewUser(isNewUser);
            response.setName(user.getName() != null ? user.getName() : email);
            return response;

        } catch (Exception e) {
            log.error("Google authentication failed", e);
            throw new IllegalArgumentException("Google Authentication Failed: " + e.getMessage());
        }
    }

    private AuthResponse getMockGoogleLogin(String rawToken) {
        log.warn("Using mock Google token verification (placeholder Client ID or Token detected).");
        String mockGoogleId = "mock-google-id-12345";
        String mockEmail = "demo.citizen@gmail.com";
        String mockName = "Demo Citizen";

        User user = userRepository.findByGoogleId(mockGoogleId)
                .or(() -> userRepository.findByEmail(mockEmail))
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(mockEmail);
                    newUser.setGoogleId(mockGoogleId);
                    newUser.setRole(Role.ROLE_CITIZEN);
                    newUser.setName(mockName);
                    return userRepository.save(newUser);
                });

        if (user.getName() == null || user.getName().isBlank()) {
            user.setName(mockName);
            user = userRepository.save(user);
        }

        boolean isNewUser = (user.getRole() == Role.ROLE_CITIZEN && user.getVoterId() == null);
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setNewUser(isNewUser);
        response.setName(user.getName());
        return response;
    }
}
