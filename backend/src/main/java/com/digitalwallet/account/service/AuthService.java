package com.digitalwallet.account.service;

import com.digitalwallet.shared.exception.AuthInvalidCredentialsException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.JwtIssuer;
import com.digitalwallet.account.dto.LoginRequest;
import com.digitalwallet.account.dto.LoginResponse;
import com.digitalwallet.account.persistence.AccountEntity;
import com.digitalwallet.account.persistence.AccountRepository;

import io.quarkus.runtime.Startup;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import java.util.Locale;
import java.util.Optional;

/**
 * Login service — issues a JWT on success and surfaces a uniform failure envelope on
 * either negative branch.
 *
 * <p>Enumeration-resistance pattern ({@code .claude/rules/security.md §2}): when the email
 * does not exist we still execute one Argon2id verify against a static sentinel hash,
 * so the wall-clock time of the "no such account" branch matches the "wrong password" branch.
 * Both branches throw {@link AuthInvalidCredentialsException} with the identical fixed
 * message {@value #INVALID_CREDENTIALS_MESSAGE} — the wire envelope is byte-identical.
 */
@Startup
@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    /** Fixed, byte-stable message — both negative branches return the same envelope. */
    static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    /** Sentinel plaintext — never matches any real password (length and characters chosen). */
    private static final String SENTINEL_PASSWORD =
            "sentinel-auth-check-placeholder-never-a-real-user-password";

    private final AccountRepository accountRepository;
    private final Argon2Hasher hasher;
    private final JwtIssuer jwtIssuer;

    private volatile String sentinelHash;

    public AuthService(AccountRepository accountRepository, Argon2Hasher hasher, JwtIssuer jwtIssuer) {
        this.accountRepository = accountRepository;
        this.hasher = hasher;
        this.jwtIssuer = jwtIssuer;
    }

    @PostConstruct
    void initSentinel() {
        // Compute once at startup. Never log the value.
        this.sentinelHash = hasher.hash(SENTINEL_PASSWORD);
        LOG.info("Sentinel hash initialised");
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String emailLower = request.email().toLowerCase(Locale.ROOT);
        Optional<AccountEntity> accountOpt = accountRepository.findByEmailLower(emailLower);

        if (accountOpt.isEmpty()) {
            // Constant-time path: run one Argon2id verify against the sentinel so the
            // wall-clock budget matches the "wrong password" branch.
            hasher.verify(request.password(), sentinelHash);
            throw new AuthInvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        AccountEntity account = accountOpt.get();
        if (!hasher.verify(request.password(), account.passwordHash)) {
            throw new AuthInvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }

        String token = jwtIssuer.issue(account.id, account.role);
        return new LoginResponse(token, "Bearer", jwtIssuer.ttlSeconds());
    }
}
