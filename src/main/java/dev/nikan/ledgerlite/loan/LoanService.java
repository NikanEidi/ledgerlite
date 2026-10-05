package dev.nikan.ledgerlite.loan;

import dev.nikan.ledgerlite.user.User;
import dev.nikan.ledgerlite.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LoanService {

    private static final int MIN_APPROVAL_SCORE = 650;
    private static final BigDecimal MAX_APPROVED_AMOUNT = new BigDecimal("50000.0000");

    private final LoanApplicationRepository loanRepository;
    private final UserRepository userRepository;
    private final CreditScoreClient creditScoreClient;

    public LoanService(LoanApplicationRepository loanRepository,
                       UserRepository userRepository,
                       CreditScoreClient creditScoreClient) {
        this.loanRepository = loanRepository;
        this.userRepository = userRepository;
        this.creditScoreClient = creditScoreClient;
    }

    @Transactional
    public LoanApplication apply(String applicantEmail, BigDecimal amount) {
        User applicant = findUserByEmail(applicantEmail);

        LoanApplication loan = new LoanApplication(applicant, amount);
        loan = loanRepository.save(loan);

        loan.moveTo(LoanStatus.UNDER_REVIEW);

        int score = creditScoreClient.fetchScore(applicantEmail);
        loan.recordCreditScore(score);

        boolean approved = score >= MIN_APPROVAL_SCORE && amount.compareTo(MAX_APPROVED_AMOUNT) <= 0;
        loan.moveTo(approved ? LoanStatus.APPROVED : LoanStatus.REJECTED);

        return loan;
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> listLoans(String applicantEmail) {
        User applicant = findUserByEmail(applicantEmail);
        return loanRepository.findByApplicantId(applicant.getId());
    }

    @Transactional(readOnly = true)
    public LoanApplication getLoan(String applicantEmail, UUID loanId) {
        User applicant = findUserByEmail(applicantEmail);
        return loanRepository.findByIdAndApplicantId(loanId, applicant.getId())
                .orElseThrow(() -> new LoanNotFoundException(loanId));
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}