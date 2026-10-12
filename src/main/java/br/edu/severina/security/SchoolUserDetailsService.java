package br.edu.severina.security;

import br.edu.severina.repository.SchoolRepository;
import br.edu.severina.service.SchoolService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class SchoolUserDetailsService implements UserDetailsService {
    private final SchoolRepository repository;
    public SchoolUserDetailsService(SchoolRepository repository) { this.repository = repository; }
    @Override public UserDetails loadUserByUsername(String username) {
        return repository.findUserByEmail(SchoolService.normalizeEmail(username))
            .map(SchoolPrincipal::new).orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada."));
    }
}
