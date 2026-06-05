package com.digitalwallet.account.service;

import com.digitalwallet.shared.exception.ConflictException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.AccountRole;
import com.digitalwallet.account.api.dto.CreateAccountRequest;
import com.digitalwallet.account.api.dto.CreateAccountResponse;
import com.digitalwallet.account.persistence.AccountEntity;
import com.digitalwallet.account.persistence.AccountRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

/**
 * Signup service — FR1.1.
 *
 * <p>Flow:
 * <ol>
 *   <li>Normalise the email to lowercase for the uniqueness check.</li>
 *   <li>Pre-check via {@link AccountRepository#findByEmailLower(String)} — returns
 *       {@code account.email_taken} as a 409 before doing any hashing work.</li>
 *   <li>Hash the password with Argon2id.</li>
 *   <li>Persist the row with {@code role = USER} and {@code fraud_status = ACTIVE}.</li>
 * </ol>
 *
 * <p>{@link Clock} is injected so tests fix the {@code created_at} value — see
 * {@code .claude/rules/upgrade-policy.md §3}. The transaction boundary lives here (not on
 * the resource or repository) per {@code .claude/rules/backend_coding.md §3}.
 *
 * <p>No RBAC guard: signup is one of the two public endpoints permitted by the
 * default-deny rule ({@code .claude/rules/security.md §3}).
 */
@ApplicationScoped
public class AccountService {

    private static final String FRAUD_STATUS_ACTIVE = "ACTIVE";

    private final AccountRepository accountRepository;
    private final Argon2Hasher hasher;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, Argon2Hasher hasher, Clock clock) {
        this.accountRepository = accountRepository;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public CreateAccountResponse signup(CreateAccountRequest request) {
        String emailLower = request.email().toLowerCase(Locale.ROOT);

        accountRepository.findByEmailLower(emailLower).ifPresent(existing -> {
            throw new ConflictException("account.email_taken", "Email is already registered");
        });

        AccountEntity account = new AccountEntity();
        account.id = UUID.randomUUID();
        account.email = request.email();
        account.passwordHash = hasher.hash(request.password());
        account.role = AccountRole.USER;
        account.baseCurrency = request.baseCurrency();
        account.fraudStatus = FRAUD_STATUS_ACTIVE;
        account.createdAt = clock.instant();

        accountRepository.persist(account);

        return new CreateAccountResponse(account.id, account.createdAt);
    }
}
