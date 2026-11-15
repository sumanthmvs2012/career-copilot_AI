package com.careercopilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode("timing-equalizer-not-a-real-password")).thenReturn("dummy-hash");
        authService = new AuthService(users, passwordEncoder, jwtService);
    }

    private static User savedUser(long id, String email, String hash) {
        User user = new User(email, hash);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void signupHashesPasswordNormalizesEmailAndReturnsToken() {
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret-pass")).thenReturn("hashed");
        when(users.saveAndFlush(any(User.class)))
                .thenAnswer(inv -> savedUser(1L, inv.<User>getArgument(0).getEmail(), "hashed"));
        when(jwtService.issueToken(any(User.class))).thenReturn("jwt");

        AuthResponse response = authService.signup(new SignupRequest("  Jane@Example.COM ", "secret-pass"));

        assertThat(response.token()).isEqualTo("jwt");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void signupRejectsAnExistingEmail() {
        when(users.existsByEmail("jane@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("jane@example.com", "secret-pass")))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void signupTurnsAUniqueConstraintRaceIntoEmailAlreadyUsed() {
        when(users.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret-pass")).thenReturn("hashed");
        when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("jane@example.com", "secret-pass")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void signupRejectsPasswordsOverTheBcryptByteLimit() {
        // 40 two-byte characters = 80 bytes: under 72 characters, over 72 bytes.
        String longPassword = "é".repeat(40);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("jane@example.com", longPassword)))
                .isInstanceOf(PasswordTooLongException.class);
    }

    @Test
    void loginReturnsATokenForTheRightPassword() {
        User user = savedUser(7L, "jane@example.com", "hashed");
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret-pass", "hashed")).thenReturn(true);
        when(jwtService.issueToken(user)).thenReturn("jwt");

        assertThat(authService.login(new LoginRequest("JANE@example.com", "secret-pass")).token()).isEqualTo("jwt");
    }

    @Test
    void loginRefusesAWrongPassword() {
        User user = savedUser(7L, "jane@example.com", "hashed");
        when(users.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "wrong-pass")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).issueToken(any());
    }

    @Test
    void loginRefusesAnUnknownEmailAfterCheckingTheDummyHash() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "secret-pass")))
                .isInstanceOf(InvalidCredentialsException.class);
        // The hash check still runs, so unknown accounts are not faster to reject.
        verify(passwordEncoder).matches(eq("secret-pass"), eq("dummy-hash"));
    }

    @Test
    void loginRefusesAnOverlongPasswordWithoutHashing() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("jane@example.com", "x".repeat(100))))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
