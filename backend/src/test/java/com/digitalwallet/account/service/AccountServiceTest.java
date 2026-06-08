package com.digitalwallet.account.service;

import com.digitalwallet.shared.exception.ConflictException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.AccountRole;
import com.digitalwallet.account.dto.CreateAccountRequest;
import com.digitalwallet.account.dto.CreateAccountResponse;
import com.digitalwallet.account.persistence.AccountEntity;
import com.digitalwallet.account.persistence.AccountRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private Argon2Hasher hasher;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, hasher, fixedClock);
    }

    @Test
    void signup_happyPath_persistsHashedAccount_andReturnsIdAndCreatedAt() {
        when(accountRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.empty());
        when(hasher.hash("password-12chr")).thenReturn("$argon2id$v=19$m=4096,t=2,p=1$abc$def");

        CreateAccountResponse response = accountService.signup(
                new CreateAccountRequest("Alice@Example.com", "password-12chr", "USD"));

        assertThat(response.accountId()).isNotNull();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-25T12:00:00Z"));

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).persist(captor.capture());
        AccountEntity persisted = captor.getValue();
        assertThat(persisted.email).isEqualTo("Alice@Example.com");
        assertThat(persisted.passwordHash).isEqualTo("$argon2id$v=19$m=4096,t=2,p=1$abc$def");
        assertThat(persisted.role).isEqualTo(AccountRole.USER);
        assertThat(persisted.baseCurrency).isEqualTo("USD");
        assertThat(persisted.fraudStatus).isEqualTo("ACTIVE");
        assertThat(persisted.createdAt).isEqualTo(Instant.parse("2026-05-25T12:00:00Z"));
        assertThat(persisted.id).isEqualTo(response.accountId());
    }

    @Test
    void signup_lowercasesEmail_forUniquenessLookup() {
        when(accountRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.empty());
        when(hasher.hash(any())).thenReturn("hashed");

        accountService.signup(new CreateAccountRequest("Alice@Example.COM", "password-12chr", "USD"));

        verify(accountRepository, times(1)).findByEmailLower("alice@example.com");
    }

    @Test
    void signup_duplicateEmail_throwsConflictException_withAccountEmailTakenKey() {
        AccountEntity existing = new AccountEntity();
        when(accountRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> accountService.signup(
                new CreateAccountRequest("Alice@Example.com", "password-12chr", "USD")))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).errorKey())
                        .isEqualTo("account.email_taken"));

        verify(hasher, never()).hash(any());
        verify(accountRepository, never()).persist(any(AccountEntity.class));
    }

    @Test
    void signup_persistsTimestampFromInjectedClock() {
        when(accountRepository.findByEmailLower(any())).thenReturn(Optional.empty());
        when(hasher.hash(any())).thenReturn("hashed");

        CreateAccountResponse response = accountService.signup(
                new CreateAccountRequest("bob@example.com", "password-12chr", "EUR"));

        assertThat(response.createdAt()).isEqualTo(fixedClock.instant());
    }
}
