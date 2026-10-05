package dev.nikan.ledgerlite.account;

import dev.nikan.ledgerlite.user.User;
import dev.nikan.ledgerlite.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransferRepository transferRepository;

    public AccountService(AccountRepository accountRepository,
                          UserRepository userRepository,
                          TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.transferRepository = transferRepository;
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

    @Transactional(readOnly = true)
    public Account getAccount(String ownerEmail, UUID accountId) {
        User owner = findUserByEmail(ownerEmail);
        return accountRepository.findByIdAndOwnerId(accountId, owner.getId())
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Transactional
    public Transfer transfer(String initiatorEmail, UUID fromAccountId,
                             String idempotencyKey, TransferRequest request) {
        User initiator = findUserByEmail(initiatorEmail);

        Optional<Transfer> previous = transferRepository
                .findByInitiatorIdAndIdempotencyKey(initiator.getId(), idempotencyKey);
        if (previous.isPresent()) {
            return previous.get();
        }

        Account recipientPreview = accountRepository.findByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> new RecipientAccountNotFoundException(request.toAccountNumber()));

        UUID toAccountId = recipientPreview.getId();
        if (toAccountId.equals(fromAccountId)) {
            throw new SameAccountTransferException(fromAccountId);
        }

        UUID firstId = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
        UUID secondId = firstId.equals(fromAccountId) ? toAccountId : fromAccountId;

        Account first = lockAccount(firstId);
        Account second = lockAccount(secondId);

        Account from = first.getId().equals(fromAccountId) ? first : second;
        Account to = first.getId().equals(fromAccountId) ? second : first;

        if (!from.getOwner().getId().equals(initiator.getId())) {
            throw new AccountNotFoundException(fromAccountId);
        }

        from.debit(request.amount());
        to.credit(request.amount());

        Transfer transfer = new Transfer(initiator, from, to, request.amount(), idempotencyKey);
        return transferRepository.save(transfer);
    }

    @Transactional
    public Account deposit(String ownerEmail, UUID accountId, BigDecimal amount) {
        User owner = findUserByEmail(ownerEmail);

        Account account = lockAccount(accountId);
        if (!account.getOwner().getId().equals(owner.getId())) {
            throw new AccountNotFoundException(accountId);
        }

        account.credit(amount);
        return account;
    }

    @Transactional
    public Account withdraw(String ownerEmail, UUID accountId, BigDecimal amount) {
        User owner = findUserByEmail(ownerEmail);

        Account account = lockAccount(accountId);
        if (!account.getOwner().getId().equals(owner.getId())) {
            throw new AccountNotFoundException(accountId);
        }

        account.debit(amount);
        return account;
    }

    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
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