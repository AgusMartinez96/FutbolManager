package com.futbolmanager.auth;

import com.futbolmanager.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BAD_CREDENTIALS = "Usuario o contraseña incorrectos.";

    private final User.Repo users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository contextRepository;
    private final String dummyHash;

    public AuthController(
            User.Repo users, PasswordEncoder passwordEncoder, SecurityContextRepository contextRepository) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.contextRepository = contextRepository;
        this.dummyHash = passwordEncoder.encode("contraseña-inexistente");
    }

    public record CsrfResponse(String headerName, String token) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 30) @Pattern(regexp = "^[A-Za-z0-9_.]+$") String username,
            @NotBlank @Size(max = 60) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record MeResponse(UUID id, String username, String displayName) {
        static MeResponse of(AuthUser user) {
            return new MeResponse(user.id(), user.username(), user.displayName());
        }
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<MeResponse> register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (users.findByUsernameIgnoreCase(body.username()).isPresent()) {
            throw usernameTaken();
        }
        User saved;
        try {
            saved = users.saveAndFlush(
                    new User(body.username(), body.displayName(), passwordEncoder.encode(body.password())));
        } catch (DataIntegrityViolationException e) {
            throw usernameTaken();
        }
        AuthUser authUser = new AuthUser(saved.getId(), saved.getUsername(), saved.getDisplayName());
        startSession(authUser, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(MeResponse.of(authUser));
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = users.findByUsernameIgnoreCase(body.username()).orElse(null);
        boolean matches = passwordEncoder.matches(body.password(), user != null ? user.getPasswordHash() : dummyHash);
        if (user == null || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        AuthUser authUser = new AuthUser(user.getId(), user.getUsername(), user.getDisplayName());
        startSession(authUser, request, response);
        return MeResponse.of(authUser);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return MeResponse.of(user);
    }

    private void startSession(AuthUser authUser, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(authUser, null, List.of()));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private static ApiException usernameTaken() {
        return ApiException.conflict("Ese nombre de usuario ya está en uso.");
    }
}
