package dev.nikan.ledgerlite.loan;

import java.util.Set;

public enum LoanStatus {

    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED;

    public boolean canTransitionTo(LoanStatus next) {
        return switch (this) {
            case SUBMITTED -> next == UNDER_REVIEW;
            case UNDER_REVIEW -> next == APPROVED || next == REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }
}