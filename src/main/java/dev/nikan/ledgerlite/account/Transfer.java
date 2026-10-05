package dev.nikan.ledgerlite.account;

import dev.nikan.ledgerlite.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "initiator_id", nullable = false, updatable = false)
    private User initiator;

    @ManyToOne(optional = false)
    @JoinColumn(name = "from_account_id", nullable = false, updatable = false)
    private Account fromAccount;

    @ManyToOne(optional = false)
    @JoinColumn(name = "to_account_id", nullable = false, updatable = false)
    private Account toAccount;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transfer() {
        // required by JPA
    }

    public Transfer(User initiator, Account fromAccount, Account toAccount,
                    BigDecimal amount, String idempotencyKey) {
        this.initiator = initiator;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    public UUID getId() { return id; }
    public User getInitiator() { return initiator; }
    public Account getFromAccount() { return fromAccount; }
    public Account getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}