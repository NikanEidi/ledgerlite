package dev.nikan.ledgerlite.loan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class LoanStatusTest {

    @Test
    void submittedCanOnlyMoveToUnderReview() {
        assertThat(LoanStatus.SUBMITTED.canTransitionTo(LoanStatus.UNDER_REVIEW)).isTrue();
        assertThat(LoanStatus.SUBMITTED.canTransitionTo(LoanStatus.APPROVED)).isFalse();
        assertThat(LoanStatus.SUBMITTED.canTransitionTo(LoanStatus.REJECTED)).isFalse();
    }

    @Test
    void underReviewCanBeApprovedOrRejected() {
        assertThat(LoanStatus.UNDER_REVIEW.canTransitionTo(LoanStatus.APPROVED)).isTrue();
        assertThat(LoanStatus.UNDER_REVIEW.canTransitionTo(LoanStatus.REJECTED)).isTrue();
        assertThat(LoanStatus.UNDER_REVIEW.canTransitionTo(LoanStatus.SUBMITTED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = LoanStatus.class, names = {"APPROVED", "REJECTED"})
    void finalStatusesCannotMoveAnywhere(LoanStatus finalStatus) {
        for (LoanStatus next : LoanStatus.values()) {
            assertThat(finalStatus.canTransitionTo(next)).isFalse();
        }
    }
}
