package com.ledgerpay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(
        @NotNull
        UUID sourceAccountId,
        @NotNull
        UUID destinationAccountId,
        @NotNull @Positive
        BigDecimal amount,
        @NotBlank @Size(max = 100)
        String idempotencyKey
) {
}