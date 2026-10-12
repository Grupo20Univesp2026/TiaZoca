package br.edu.severina.domain;

public record UserAccount(long id, String name, String email, String passwordHash,
                          String role, String room, boolean active) implements java.io.Serializable {
    public UserView view() {
        return new UserView(id, name, email, role, room, active);
    }

    public record UserView(long id, String name, String email, String role, String room, boolean active) {}
}
