package com.poker.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /tables}.
 *
 * <p>bigBlind must equal 2 × smallBlind — enforced in the service layer
 * because Jakarta Validation lacks a cross-field constraint in the standard
 * set. Bean Validation covers individual range guards here.
 */
public record CreateTableRequest(

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be ≤ 100 characters")
    String name,

    @Min(value = 1, message = "smallBlind must be at least 1")
    @Max(value = 10_000, message = "smallBlind must be ≤ 10 000")
    int smallBlind,

    @Min(value = 2, message = "bigBlind must be at least 2")
    @Max(value = 20_000, message = "bigBlind must be ≤ 20 000")
    int bigBlind
) {}
