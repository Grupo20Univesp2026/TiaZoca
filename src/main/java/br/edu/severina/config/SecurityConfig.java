package br.edu.severina.config;

import br.edu.severina.repository.SchoolRepository;
import br.edu.severina.security.ActiveAccountFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public Clock schoolClock(@Value("${app.time-zone}") String zone) { return Clock.system(ZoneId.of(zone)); }
    @Bean public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    @Bean public SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean public HttpSessionCsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean public SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrfRepository) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfRepository)));
    }

    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, SchoolRepository repository,
        ObjectMapper json, SecurityContextRepository contextRepository, HttpSessionCsrfTokenRepository csrfRepository) throws Exception {
        http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository))
            .securityContext(context -> context.securityContextRepository(contextRepository).requireExplicitSave(true))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/login.html", "/cadastro.html", "/css/**", "/js/**", "/assets/**",
                    "/img/**", "/styles.css", "/app.js", "/favicon.ico", "/api/csrf", "/actuator/health", "/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/secretaria.html", "/api/teachers/**").hasRole("SECRETARY")
                .requestMatchers(HttpMethod.POST, "/api/bookings").hasRole("TEACHER")
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(401);
                        response.setContentType("application/json;charset=UTF-8");
                        json.writeValue(response.getWriter(), Map.of("message", "Entre na sua conta para continuar."));
                    } else response.sendRedirect("/login.html");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(403);
                        response.setContentType("application/json;charset=UTF-8");
                        String message = exception instanceof CsrfException
                            ? "Sua sessão precisa ser atualizada. Recarregue a página e tente novamente."
                            : "Você não tem permissão para realizar esta ação.";
                        json.writeValue(response.getWriter(), Map.of("message", message));
                    } else response.sendRedirect("/agendamentos.html");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterAfter(new ActiveAccountFilter(repository), SecurityContextHolderFilter.class);
        return http.build();
    }
}
