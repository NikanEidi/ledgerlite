package dev.nikan.ledgerlite.loan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SubmitLoanRequest(

        @NotNull
        @DecimalMin(value = "0.0001", message = "must be greater than zero")
        BigDecimal amount
) {
}