package br.edu.severina.config;

import br.edu.severina.repository.SchoolRepository;
import br.edu.severina.service.SchoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SecretaryInitializer implements ApplicationRunner {
    private final SchoolRepository repository;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    private final String name;

    public SecretaryInitializer(SchoolRepository repository, PasswordEncoder encoder,
        @Value("${app.secretary.email}") String email, @Value("${app.secretary.password}") String password,
        @Value("${app.secretary.name}") String name) {
        this.repository = repository;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Configure SECRETARY_EMAIL e SECRETARY_PASSWORD antes de iniciar o sistema.");
        }
        if (email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || name.isBlank() || name.length() > 120) {
            throw new IllegalStateException("Confira SECRETARY_EMAIL e SECRETARY_NAME: informe um e-mail válido e nome de até 120 caracteres.");
        }
        SchoolService.validatePassword(password);
        String normalizedEmail = SchoolService.normalizeEmail(email);
        repository.findUserByEmail(normalizedEmail).ifPresentOrElse(user -> {
            if (!"SECRETARY".equals(user.role()) || !user.active()) {
                throw new IllegalStateException("O e-mail da secretaria já pertence a outra conta. Configure outro SECRETARY_EMAIL.");
            }
        }, () -> repository.createUser(name.strip(), normalizedEmail, encoder.encode(password), "SECRETARY"));
    }
}
