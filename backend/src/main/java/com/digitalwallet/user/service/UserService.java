package com.digitalwallet.user.service;

import com.digitalwallet.shared.exception.ConflictException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.UserRole;
import com.digitalwallet.user.api.dto.CreateUserRequest;
import com.digitalwallet.user.api.dto.CreateUserResponse;
import com.digitalwallet.user.persistence.UserEntity;
import com.digitalwallet.user.persistence.UserRepository;

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
 *   <li>Pre-check via {@link UserRepository#findByEmailLower(String)} — returns
 *       {@code user.email_taken} as a 409 before doing any hashing work.</li>
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
public class UserService {

    private static final String FRAUD_STATUS_ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final Argon2Hasher hasher;
    private final Clock clock;

    public UserService(UserRepository userRepository, Argon2Hasher hasher, Clock clock) {
        this.userRepository = userRepository;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Transactional
    public CreateUserResponse signup(CreateUserRequest request) {
        String emailLower = request.email().toLowerCase(Locale.ROOT);

        userRepository.findByEmailLower(emailLower).ifPresent(existing -> {
            throw new ConflictException("user.email_taken", "Email is already registered");
        });

        UserEntity user = new UserEntity();
        user.id = UUID.randomUUID();
        user.email = request.email();
        user.passwordHash = hasher.hash(request.password());
        user.role = UserRole.USER;
        user.baseCurrency = request.baseCurrency();
        user.fraudStatus = FRAUD_STATUS_ACTIVE;
        user.createdAt = clock.instant();

        userRepository.persist(user);

        return new CreateUserResponse(user.id, user.createdAt);
    }
}
