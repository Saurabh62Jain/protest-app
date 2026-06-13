package com.civic.action.service;

import com.civic.action.config.JwtService;
import com.civic.action.dto.response.AuthResponse;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private GoogleAuthService googleAuthService;

    @Test
    public void testMockGoogleLoginNewUser() {
        String idToken = "mock-token-12345";
        String clientId = "YOUR_GOOGLE_CLIENT_ID";

        User mockSavedUser = new User();
        mockSavedUser.setId(10L);
        mockSavedUser.setEmail("demo.citizen@gmail.com");
        mockSavedUser.setName("Demo Citizen");
        mockSavedUser.setGoogleId("mock-google-id-12345");
        mockSavedUser.setRole(Role.ROLE_CITIZEN);
        mockSavedUser.setVoterId(null); // New user has no Voter ID linked yet

        when(userRepository.findByGoogleId("mock-google-id-12345")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("demo.citizen@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(mockSavedUser);
        when(jwtService.generateToken("demo.citizen@gmail.com", "ROLE_CITIZEN")).thenReturn("mock-jwt-token-xyz");

        AuthResponse response = googleAuthService.verifyGoogleTokenAndLogin(idToken, clientId);

        assertNotNull(response);
        assertEquals("mock-jwt-token-xyz", response.getToken());
        assertEquals("ROLE_CITIZEN", response.getRole());
        assertTrue(response.isNewUser()); // Should be true since voterId is null

        verify(userRepository, times(1)).save(any(User.class));
        verify(jwtService, times(1)).generateToken("demo.citizen@gmail.com", "ROLE_CITIZEN");
    }

    @Test
    public void testMockGoogleLoginExistingUser() {
        String idToken = "mock-token-12345";
        String clientId = "YOUR_GOOGLE_CLIENT_ID";

        User mockUser = new User();
        mockUser.setId(10L);
        mockUser.setEmail("demo.citizen@gmail.com");
        mockUser.setName("Demo Citizen");
        mockUser.setGoogleId("mock-google-id-12345");
        mockUser.setRole(Role.ROLE_CITIZEN);
        mockUser.setVoterId("VOTER777"); // Existing user has Voter ID

        when(userRepository.findByGoogleId("mock-google-id-12345")).thenReturn(Optional.of(mockUser));
        when(jwtService.generateToken("demo.citizen@gmail.com", "ROLE_CITIZEN")).thenReturn("mock-jwt-token-xyz");

        AuthResponse response = googleAuthService.verifyGoogleTokenAndLogin(idToken, clientId);

        assertNotNull(response);
        assertEquals("mock-jwt-token-xyz", response.getToken());
        assertFalse(response.isNewUser()); // Should be false because voterId is present

        verify(userRepository, never()).save(any(User.class));
    }
}
