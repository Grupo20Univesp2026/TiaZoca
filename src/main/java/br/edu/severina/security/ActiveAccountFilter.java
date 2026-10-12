package br.edu.severina.security;

import br.edu.severina.repository.SchoolRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ActiveAccountFilter extends OncePerRequestFilter {
    private final SchoolRepository repository;
    public ActiveAccountFilter(SchoolRepository repository) { this.repository = repository; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SchoolPrincipal principal) {
            boolean active = repository.findUser(principal.id()).map(user -> user.active()).orElse(false);
            if (!active) {
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) request.getSession(false).invalidate();
            }
        }
        chain.doFilter(request, response);
    }
}
