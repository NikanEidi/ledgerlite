package dev.nikan.ledgerlite.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByOwnerId(UUID ownerId);

    Optional<Account> findByIdAndOwnerId(UUID id, UUID ownerId);
}