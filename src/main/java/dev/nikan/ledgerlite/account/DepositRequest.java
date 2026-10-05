package dev.nikan.ledgerlite.account;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DepositRequest(

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        BigDecimal amount
) {
}