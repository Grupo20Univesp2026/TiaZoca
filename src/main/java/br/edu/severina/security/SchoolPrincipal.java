package br.edu.severina.security;

import br.edu.severina.domain.UserAccount;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record SchoolPrincipal(UserAccount account) implements UserDetails {
    public long id() { return account.id(); }
    @Override public String getUsername() { return account.email(); }
    @Override public String getPassword() { return account.passwordHash(); }
    @Override public boolean isEnabled() { return account.active(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + account.role()));
    }
}
