package com.civic.action.dto.response;

import lombok.Data;

@Data
public class AuthResponse {
    private String token;
    private String role;
    private boolean isNewUser;
    private String name;
}
