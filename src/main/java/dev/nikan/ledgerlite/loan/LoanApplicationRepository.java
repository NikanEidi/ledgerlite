package dev.nikan.ledgerlite.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    List<LoanApplication> findByApplicantId(UUID applicantId);

    Optional<LoanApplication> findByIdAndApplicantId(UUID id, UUID applicantId);
}