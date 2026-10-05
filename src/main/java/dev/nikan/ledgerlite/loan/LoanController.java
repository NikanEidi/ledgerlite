package dev.nikan.ledgerlite.loan;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/loans")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> apply(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody SubmitLoanRequest request) {
        LoanApplication loan = loanService.apply(jwt.getSubject(), request.amount());
        return ResponseEntity.status(HttpStatus.CREATED).body(LoanResponse.from(loan));
    }

    @GetMapping
    public ResponseEntity<List<LoanResponse>> listLoans(@AuthenticationPrincipal Jwt jwt) {
        List<LoanResponse> loans = loanService.listLoans(jwt.getSubject()).stream()
                .map(LoanResponse::from)
                .toList();
        return ResponseEntity.ok(loans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanResponse> getLoan(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID id) {
        LoanApplication loan = loanService.getLoan(jwt.getSubject(), id);
        return ResponseEntity.ok(LoanResponse.from(loan));
    }
}