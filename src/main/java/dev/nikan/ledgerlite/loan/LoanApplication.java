package dev.nikan.ledgerlite.loan;

import dev.nikan.ledgerlite.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loan_application")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "applicant_id", nullable = false, updatable = false)
    private User applicant;

    @Column(name = "requested_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal requestedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanStatus status;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected LoanApplication() {
        // required by JPA
    }

    public LoanApplication(User applicant, BigDecimal requestedAmount) {
        this.applicant = applicant;
        this.requestedAmount = requestedAmount;
        this.status = LoanStatus.SUBMITTED;
    }

    public void moveTo(LoanStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Cannot move loan from " + status + " to " + next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void recordCreditScore(int score) {
        this.creditScore = score;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getApplicant() { return applicant; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public LoanStatus getStatus() { return status; }
    public Integer getCreditScore() { return creditScore; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}