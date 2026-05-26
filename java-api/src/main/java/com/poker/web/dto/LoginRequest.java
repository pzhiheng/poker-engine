package com.poker.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /auth/login}. */
public record LoginRequest(

    @NotBlank(message = "username is required")
    String username,

    @NotBlank(message = "password is required")
    String password
) {}
