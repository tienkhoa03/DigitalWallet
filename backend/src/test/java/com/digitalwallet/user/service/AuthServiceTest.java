package com.digitalwallet.user.service;

import com.digitalwallet.shared.exception.AuthInvalidCredentialsException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.JwtIssuer;
import com.digitalwallet.shared.security.UserRole;
import com.digitalwallet.user.api.dto.LoginRequest;
import com.digitalwallet.user.api.dto.LoginResponse;
import com.digitalwallet.user.persistence.UserEntity;
import com.digitalwallet.user.persistence.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SENTINEL_HASH_VALUE =
            "$argon2id$v=19$m=4096,t=2,p=1$AAAAAAAAAAAAAAAAAAAAAA$BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBQ";

    @Mock
    private UserRepository userRepository;

    @Mock
    private Argon2Hasher hasher;

    @Mock
    private JwtIssuer jwtIssuer;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        // Stub sentinel hash production used by @PostConstruct.
        when(hasher.hash(anyString())).thenReturn(SENTINEL_HASH_VALUE);

        authService = new AuthService(userRepository, hasher, jwtIssuer);
        authService.initSentinel();
    }

    @Test
    void login_happyPath_returnsBearerTokenWithTtl() {
        UUID userId = UUID.randomUUID();
        UserEntity user = userBuilder(userId, UserRole.USER);
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(user));
        when(hasher.verify("correct-pass", user.passwordHash)).thenReturn(true);
        when(jwtIssuer.issue(userId, UserRole.USER)).thenReturn("signed.jwt.value");
        when(jwtIssuer.ttlSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(
                new LoginRequest("Alice@Example.com", "correct-pass"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void login_wrongPassword_throwsAuthInvalidCredentials_withFixedMessage() {
        UserEntity user = userBuilder(UUID.randomUUID(), UserRole.USER);
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(user));
        when(hasher.verify("wrong-pass", user.passwordHash)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-pass")))
                .isInstanceOf(AuthInvalidCredentialsException.class)
                .satisfies(ex -> {
                    AuthInvalidCredentialsException e = (AuthInvalidCredentialsException) ex;
                    assertThat(e.errorKey()).isEqualTo("auth.invalid_credentials");
                    assertThat(e.getMessage()).isEqualTo(AuthService.INVALID_CREDENTIALS_MESSAGE);
                });
    }

    @Test
    void login_unknownEmail_throwsAuthInvalidCredentials_withSameMessage() {
        when(userRepository.findByEmailLower("ghost@example.com")).thenReturn(Optional.empty());
        // Sentinel verify returns false (no match) — but we never reach the wrong-password
        // branch because the user is missing.
        when(hasher.verify(eq("anything"), eq(SENTINEL_HASH_VALUE))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "anything")))
                .isInstanceOf(AuthInvalidCredentialsException.class)
                .satisfies(ex -> {
                    AuthInvalidCredentialsException e = (AuthInvalidCredentialsException) ex;
                    assertThat(e.errorKey()).isEqualTo("auth.invalid_credentials");
                    assertThat(e.getMessage()).isEqualTo(AuthService.INVALID_CREDENTIALS_MESSAGE);
                });
    }

    @Test
    void login_unknownEmail_stillCallsHasherVerifyAgainstSentinel() {
        when(userRepository.findByEmailLower(any())).thenReturn(Optional.empty());
        when(hasher.verify(anyString(), eq(SENTINEL_HASH_VALUE))).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "anything")))
                .isInstanceOf(AuthInvalidCredentialsException.class);

        // The sentinel-verify call is the enumeration-resistance hinge: it must happen.
        verify(hasher, atLeastOnce()).verify("anything", SENTINEL_HASH_VALUE);
    }

    @Test
    void login_negativeBranches_returnIdenticalExceptionEnvelope() {
        UserEntity user = userBuilder(UUID.randomUUID(), UserRole.USER);
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByEmailLower("ghost@example.com")).thenReturn(Optional.empty());
        when(hasher.verify(anyString(), anyString())).thenReturn(false);

        AuthInvalidCredentialsException wrongPassword = (AuthInvalidCredentialsException)
                catchException(() -> authService.login(new LoginRequest("alice@example.com", "x-wrong")));
        AuthInvalidCredentialsException unknownEmail = (AuthInvalidCredentialsException)
                catchException(() -> authService.login(new LoginRequest("ghost@example.com", "x-wrong")));

        assertThat(wrongPassword.errorKey()).isEqualTo(unknownEmail.errorKey());
        assertThat(wrongPassword.getMessage()).isEqualTo(unknownEmail.getMessage());
    }

    @Test
    void login_lowercasesEmail_forRepositoryLookup() {
        UserEntity user = userBuilder(UUID.randomUUID(), UserRole.USER);
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(user));
        when(hasher.verify(any(), any())).thenReturn(true);
        when(jwtIssuer.issue(any(), any())).thenReturn("token");
        when(jwtIssuer.ttlSeconds()).thenReturn(3600L);

        authService.login(new LoginRequest("ALICE@example.COM", "anything-12-chars"));

        verify(userRepository, times(1)).findByEmailLower("alice@example.com");
    }

    private static UserEntity userBuilder(UUID id, UserRole role) {
        UserEntity u = new UserEntity();
        u.id = id;
        u.email = "alice@example.com";
        u.passwordHash = "$argon2id$v=19$m=4096,t=2,p=1$existing-salt$existing-hash";
        u.role = role;
        u.baseCurrency = "USD";
        u.fraudStatus = "ACTIVE";
        return u;
    }

    private static Throwable catchException(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
