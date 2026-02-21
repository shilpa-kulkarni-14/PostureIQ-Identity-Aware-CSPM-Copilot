package com.cspm.controller;

import com.cspm.model.*;
import com.cspm.repository.UserRepository;
import com.cspm.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000L;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> loginAttempts =
            new ConcurrentHashMap<>();

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Username already exists"));
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email already exists"));
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role("USER")
                .build();

        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthRequest request) {
        if (isRateLimited(request.getUsername())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many login attempts. Please try again later."));
        }

        recordAttempt(request.getUsername());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow();

        String token = jwtService.generateToken(user);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation failed");
        response.put("fields", fieldErrors);
        return response;
    }

    private boolean isRateLimited(String username) {
        ConcurrentLinkedDeque<Long> attempts = loginAttempts.get(username);
        if (attempts == null) {
            return false;
        }
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!attempts.isEmpty() && attempts.peekFirst() < cutoff) {
            attempts.pollFirst();
        }
        return attempts.size() >= MAX_ATTEMPTS;
    }

    private void recordAttempt(String username) {
        loginAttempts.computeIfAbsent(username, k -> new ConcurrentLinkedDeque<>())
                .addLast(System.currentTimeMillis());
    }
}
