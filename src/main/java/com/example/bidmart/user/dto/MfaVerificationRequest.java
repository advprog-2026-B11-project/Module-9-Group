package com.example.bidmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaVerificationRequest {
    @NotBlank(message = "Temporary token is required")
    private String tempToken;

    @NotBlank(message = "TOTP code is required")
    private String code;
}
