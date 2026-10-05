package dev.nikan.ledgerlite.account;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<String> openAccount(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.openAccount(jwt.getSubject(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Account opened: " + account.getAccountNumber());
    }
}