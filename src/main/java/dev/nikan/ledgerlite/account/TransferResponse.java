package dev.nikan.ledgerlite.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        Instant createdAt
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getFromAccount().getAccountNumber(),
                transfer.getToAccount().getAccountNumber(),
                transfer.getAmount(),
                transfer.getCreatedAt()
        );
    }
}