package com.digitalwallet.user.service;

import com.digitalwallet.shared.exception.ConflictException;
import com.digitalwallet.shared.security.Argon2Hasher;
import com.digitalwallet.shared.security.UserRole;
import com.digitalwallet.user.api.dto.CreateUserRequest;
import com.digitalwallet.user.api.dto.CreateUserResponse;
import com.digitalwallet.user.persistence.UserEntity;
import com.digitalwallet.user.persistence.UserRepository;

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
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Argon2Hasher hasher;

    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, hasher, fixedClock);
    }

    @Test
    void signup_happyPath_persistsHashedUser_andReturnsIdAndCreatedAt() {
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.empty());
        when(hasher.hash("password-12chr")).thenReturn("$argon2id$v=19$m=4096,t=2,p=1$abc$def");

        CreateUserResponse response = userService.signup(
                new CreateUserRequest("Alice@Example.com", "password-12chr", "USD"));

        assertThat(response.userId()).isNotNull();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-05-25T12:00:00Z"));

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).persist(captor.capture());
        UserEntity persisted = captor.getValue();
        assertThat(persisted.email).isEqualTo("Alice@Example.com");
        assertThat(persisted.passwordHash).isEqualTo("$argon2id$v=19$m=4096,t=2,p=1$abc$def");
        assertThat(persisted.role).isEqualTo(UserRole.USER);
        assertThat(persisted.baseCurrency).isEqualTo("USD");
        assertThat(persisted.fraudStatus).isEqualTo("ACTIVE");
        assertThat(persisted.createdAt).isEqualTo(Instant.parse("2026-05-25T12:00:00Z"));
        assertThat(persisted.id).isEqualTo(response.userId());
    }

    @Test
    void signup_lowercasesEmail_forUniquenessLookup() {
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.empty());
        when(hasher.hash(any())).thenReturn("hashed");

        userService.signup(new CreateUserRequest("Alice@Example.COM", "password-12chr", "USD"));

        verify(userRepository, times(1)).findByEmailLower("alice@example.com");
    }

    @Test
    void signup_duplicateEmail_throwsConflictException_withUserEmailTakenKey() {
        UserEntity existing = new UserEntity();
        when(userRepository.findByEmailLower("alice@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> userService.signup(
                new CreateUserRequest("Alice@Example.com", "password-12chr", "USD")))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).errorKey())
                        .isEqualTo("user.email_taken"));

        verify(hasher, never()).hash(any());
        verify(userRepository, never()).persist(any(UserEntity.class));
    }

    @Test
    void signup_persistsTimestampFromInjectedClock() {
        when(userRepository.findByEmailLower(any())).thenReturn(Optional.empty());
        when(hasher.hash(any())).thenReturn("hashed");

        CreateUserResponse response = userService.signup(
                new CreateUserRequest("bob@example.com", "password-12chr", "EUR"));

        assertThat(response.createdAt()).isEqualTo(fixedClock.instant());
    }
}
