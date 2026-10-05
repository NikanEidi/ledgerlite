package dev.nikan.ledgerlite.account;

import java.util.UUID;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException(UUID accountId) {
        super("Cannot transfer to the same account: " + accountId);
    }
}