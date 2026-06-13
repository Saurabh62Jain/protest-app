package com.civic.action.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoterLinkRequest {
    @NotBlank(message = "Voter ID is required")
    private String voterId;
}
