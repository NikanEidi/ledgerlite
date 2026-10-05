package dev.nikan.ledgerlite.account;

import jakarta.validation.constraints.NotNull;

public record OpenAccountRequest(

        @NotNull
        AccountType type
) {
}