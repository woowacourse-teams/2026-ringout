package com.ringout.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TestLoginRequest(
    @NotBlank String testUserId
) {
}
