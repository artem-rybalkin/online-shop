
package com.shop.controller;

import com.shop.dto.AuthRequest;
import com.shop.dto.AuthResponse;
import com.shop.dto.LoginRequest;
import com.shop.model.User;
import com.shop.security.JwtUtil;
import com.shop.security.SecurityUtils;
import com.shop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Registration, login, and session management via httpOnly JWT cookie")
public class AuthController {

    private static final String COOKIE_NAME = "jwt";
    private static final long COOKIE_MAX_AGE_SECONDS = 60 * 60 * 10; // matches JwtUtil token expiration

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    public AuthController(AuthenticationManager authenticationManager, UserService userService, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @SuppressWarnings("null")
    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates a user and sets a httpOnly jwt cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account created",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Username already exists, or validation failed (blank username/password, invalid email)")
    })
    public ResponseEntity<?> register(@Valid @RequestBody AuthRequest request) {
        if (userService.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }
        User user = userService.registerUser(request.getUsername(), request.getPassword(), request.getEmail());
        return authenticatedResponse(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Authenticates with username + password (no email field) and sets a httpOnly jwt cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation failed (blank username/password)"),
        @ApiResponse(responseCode = "401", description = "Invalid username or password")
    })
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userService.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return authenticatedResponse(user);
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Expires the jwt cookie.")
    @ApiResponse(responseCode = "200", description = "Logged out")
    public ResponseEntity<?> logout() {
        ResponseCookie expiredCookie = buildCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expiredCookie.toString())
                .body(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user", description = "Requires a valid jwt cookie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Current user returned",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<?> me() {
        String username = SecurityUtils.getCurrentUsername();
        if (username == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userService.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return ResponseEntity.ok(new AuthResponse(user.getUsername(), user.getEmail()));
    }

    private ResponseEntity<?> authenticatedResponse(User user) {
        String token = jwtUtil.generateToken(user.getUsername(), user.getEmail(), user.getRole());
        ResponseCookie cookie = buildCookie(token != null ? token : "", COOKIE_MAX_AGE_SECONDS);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthResponse(user.getUsername(), user.getEmail()));
    }

    private ResponseCookie buildCookie(@NonNull String value, long maxAgeSeconds) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}
