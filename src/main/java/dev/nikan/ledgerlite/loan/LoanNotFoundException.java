package dev.nikan.ledgerlite.loan;

import java.util.UUID;

public class LoanNotFoundException extends RuntimeException {

    public LoanNotFoundException(UUID loanId) {
        super("Loan application not found: " + loanId);
    }
}