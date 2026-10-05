package dev.nikan.ledgerlite.account;

import dev.nikan.ledgerlite.user.Role;
import dev.nikan.ledgerlite.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account newAccount() {
        User owner = new User("owner@test.com", "hash", "Owner", Role.CUSTOMER);
        return new Account(owner, "1234567890", AccountType.CHECKING);
    }

    @Test
    void newAccountStartsWithZeroBalance() {
        assertThat(newAccount().getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void creditIncreasesBalance() {
        Account account = newAccount();

        account.credit(new BigDecimal("100.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void debitDecreasesBalanceWhenFundsAreEnough() {
        Account account = newAccount();
        account.credit(new BigDecimal("100.00"));

        account.debit(new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
    }

    @Test
    void debitRejectsAmountGreaterThanBalance() {
        Account account = newAccount();
        account.credit(new BigDecimal("50.00"));

        assertThatThrownBy(() -> account.debit(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("50.00");
    }
}
