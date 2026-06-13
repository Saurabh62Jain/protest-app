package com.civic.action.service;

import com.civic.action.config.JwtService;
import com.civic.action.dto.response.AuthResponse;
import com.civic.action.model.postgres.Role;
import com.civic.action.model.postgres.User;
import com.civic.action.repository.postgres.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse verifyOtpAndLogin(String mobileNumber, String otpCode) {
        // 1. Verify OTP
        boolean isValid = otpService.validateOtp(mobileNumber, otpCode);
        if (!isValid) {
            throw new IllegalArgumentException("Invalid or expired OTP");
        }

        // 2. Fetch or Create User in PostgreSQL
        boolean isNewUser = false;
        User user = userRepository.findByMobileNumber(mobileNumber).orElse(null);

        if (user == null) {
            user = new User();
            user.setMobileNumber(mobileNumber);
            user.setRole(Role.ROLE_CITIZEN); // Citizens sign up by default
            user = userRepository.save(user);
            isNewUser = true;
        }

        // If the user exists but hasn't linked a voter ID, we still signal isNewUser to prompt for it
        if (user.getRole() == Role.ROLE_CITIZEN && user.getVoterId() == null) {
            isNewUser = true;
        }

        // 3. Generate stateless JWT
        String token = jwtService.generateToken(user.getMobileNumber(), user.getRole().name());

        AuthResponse response = new AuthResponse();
        response.setToken(token);
        response.setRole(user.getRole().name());
        response.setNewUser(isNewUser);
        response.setName(user.getName() != null ? user.getName() : user.getMobileNumber());
        return response;
    }

    @Transactional
    public void linkVoterId(String mobileNumber, String voterId) {
        User user = userRepository.findByMobileNumber(mobileNumber)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (userRepository.existsByVoterId(voterId) && !voterId.equals(user.getVoterId())) {
            throw new IllegalArgumentException("Voter ID is already registered by another user");
        }

        user.setVoterId(voterId);
        userRepository.save(user);
    }
}
