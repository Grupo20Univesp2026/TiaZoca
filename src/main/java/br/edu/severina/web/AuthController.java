package br.edu.severina.web;

import br.edu.severina.domain.ApiException;
import br.edu.severina.domain.UserAccount;
import br.edu.severina.security.SchoolPrincipal;
import br.edu.severina.service.SchoolService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final SchoolService service;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;

    public AuthController(SchoolService service, AuthenticationManager authenticationManager,
        SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
        this.service = service;
        this.authenticationManager = authenticationManager;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
    }

    public record RegisterRequest(
        @NotBlank(message = "Informe seu nome.") @Size(max = 120, message = "O nome deve ter até 120 caracteres.") String name,
        @NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 254, message = "O e-mail deve ter até 254 caracteres.") String email,
        @NotBlank(message = "Informe sua senha.") @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.") String password) {}

    public record LoginRequest(
        @NotBlank(message = "Informe seu e-mail.") @Email(message = "Informe um e-mail válido.") @Size(max = 254, message = "Informe um e-mail válido.") String email,
        @NotBlank(message = "Informe sua senha.") @Size(max = 72, message = "Informe uma senha válida.") String password) {}

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest body) {
        service.register(body.name(), body.email(), body.password());
        return Map.of("message", "Conta criada! Entre no sistema e aguarde a secretaria definir sua sala.");
    }

    @PostMapping("/auth/login")
    public UserAccount.UserView login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        if (body.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos, ou conta removida pela secretaria.");
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(SchoolService.normalizeEmail(body.email()), body.password()));
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos, ou conta removida pela secretaria.");
        }
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        SchoolPrincipal principal = (SchoolPrincipal) authentication.getPrincipal();
        return service.currentUser(principal.id()).view();
    }

    @GetMapping("/auth/me")
    public UserAccount.UserView me(@AuthenticationPrincipal SchoolPrincipal principal) {
        return service.currentUser(principal.id()).view();
    }
}
