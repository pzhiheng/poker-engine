package com.poker.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /auth/register}. */
public record RegisterRequest(

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must be 3–50 characters")
    String username,

    @NotBlank(message = "password is required")
    @Size(min = 8, message = "password must be at least 8 characters")
    String password
) {}
