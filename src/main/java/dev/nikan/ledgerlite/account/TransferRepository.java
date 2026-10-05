package dev.nikan.ledgerlite.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByInitiatorIdAndIdempotencyKey(UUID initiatorId, String idempotencyKey);
}