package dev.nikan.ledgerlite.account;

import dev.nikan.ledgerlite.user.User;
import dev.nikan.ledgerlite.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Account openAccount(String ownerEmail, AccountType type) {
        User owner = findUserByEmail(ownerEmail);

        Account account = new Account(owner, generateAccountNumber(), type);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts(String ownerEmail) {
        User owner = findUserByEmail(ownerEmail);
        return accountRepository.findByOwnerId(owner.getId());
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private String generateAccountNumber() {
        long number = 1_000_000_000L + Math.floorMod(RANDOM.nextLong(), 9_000_000_000L);
        return String.valueOf(number);
    }
}