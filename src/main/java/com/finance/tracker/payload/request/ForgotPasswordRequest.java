package com.finance.tracker.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ForgotPasswordRequest {
    // Accept either email or username (demo mode).
    @NotBlank
    private String identifier;
}

