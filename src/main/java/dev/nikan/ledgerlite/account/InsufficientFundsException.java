package dev.nikan.ledgerlite.account;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID accountId, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds in account " + accountId
                + ": requested " + requested + ", available " + available);
    }
}