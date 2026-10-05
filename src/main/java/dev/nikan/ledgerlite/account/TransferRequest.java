package dev.nikan.ledgerlite.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransferRequest(

        @NotBlank
        String toAccountNumber,

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        BigDecimal amount
) {
}