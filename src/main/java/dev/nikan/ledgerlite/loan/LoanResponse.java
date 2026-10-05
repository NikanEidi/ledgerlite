package dev.nikan.ledgerlite.loan;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanResponse(
        UUID id,
        BigDecimal requestedAmount,
        LoanStatus status,
        Integer creditScore,
        Instant createdAt,
        Instant updatedAt
) {

    public static LoanResponse from(LoanApplication loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getRequestedAmount().setScale(4),
                loan.getStatus(),
                loan.getCreditScore(),
                loan.getCreatedAt(),
                loan.getUpdatedAt()
        );
    }
}