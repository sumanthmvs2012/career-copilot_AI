package com.careercopilot.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    // Checked when the email is unknown, so a missing account takes as long as a wrong password.
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-equalizer-not-a-real-password");
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalize(request.email());
        if (exceedsBcryptLimit(request.password())) {
            throw new PasswordTooLongException();
        }
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyUsedException();
        }
        User user;
        try {
            user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Two signups for the same email raced past existsByEmail; the unique constraint wins.
            throw new EmailAlreadyUsedException();
        }
        log.info("Account created userId={}", user.getId());
        return new AuthResponse(jwtService.issueToken(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        String password = request.password();
        if (exceedsBcryptLimit(password)) {
            // Could never be a stored password; refuse exactly like any other mismatch.
            log.warn("Login failed");
            throw new InvalidCredentialsException();
        }
        User user = users.findByEmail(email).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = passwordEncoder.matches(password, hash);
        if (user == null || !matches) {
            log.warn("Login failed");
            throw new InvalidCredentialsException();
        }
        log.info("Login succeeded userId={}", user.getId());
        return new AuthResponse(jwtService.issueToken(user));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}
