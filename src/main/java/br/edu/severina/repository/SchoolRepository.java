package br.edu.severina.repository;

import br.edu.severina.domain.BookingView;
import br.edu.severina.domain.Catalog;
import br.edu.severina.domain.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class SchoolRepository {
    private final JdbcTemplate jdbc;
    private static final RowMapper<UserAccount> USER_MAPPER = (rs, row) -> new UserAccount(
        rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("password_hash"),
        rs.getString("role"), rs.getString("room"), rs.getBoolean("active"));

    public SchoolRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<UserAccount> findUserByEmail(String email) {
        return jdbc.query("SELECT * FROM app_user WHERE email = ?", USER_MAPPER, email).stream().findFirst();
    }

    public Optional<UserAccount> findUser(long id) {
        return jdbc.query("SELECT * FROM app_user WHERE id = ?", USER_MAPPER, id).stream().findFirst();
    }

    // The user lock serializes reservations with room changes and account removal.
    public Optional<UserAccount> lockUser(long id) {
        return jdbc.query("SELECT * FROM app_user WHERE id = ? FOR UPDATE", USER_MAPPER, id).stream().findFirst();
    }

    public UserAccount createUser(String name, String email, String hash, String role) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO app_user (name, email, password_hash, role) VALUES (?, ?, ?, ?)", new String[]{"id"});
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, hash);
            statement.setString(4, role);
            return statement;
        }, key);
        return findUser(key.getKey().longValue()).orElseThrow();
    }

    public List<UserAccount> teachers() {
        return jdbc.query("SELECT * FROM app_user WHERE role = 'TEACHER' ORDER BY LOWER(name), id", USER_MAPPER);
    }

    public UserAccount assignRoom(long id, String room) {
        jdbc.update("UPDATE app_user SET room = ? WHERE id = ?", room, id);
        return findUser(id).orElseThrow();
    }

    public void deactivateTeacher(long id, LocalDate today) {
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", id);
        jdbc.update("UPDATE booking SET status = 'CANCELLED' WHERE teacher_id = ? AND status = 'CONFIRMED' AND return_date >= ?",
            id, Date.valueOf(today));
    }

    public List<Catalog.Equipment> equipment() {
        return jdbc.query("SELECT id, name FROM equipment ORDER BY id",
            (rs, row) -> new Catalog.Equipment(rs.getLong("id"), rs.getString("name")));
    }

    public Optional<Catalog.Equipment> lockEquipment(long id) {
        return jdbc.query("SELECT id, name FROM equipment WHERE id = ? FOR UPDATE",
            (rs, row) -> new Catalog.Equipment(rs.getLong("id"), rs.getString("name")), id).stream().findFirst();
    }

    public boolean hasOverlap(long equipmentId, LocalDate pickup, LocalDate returns) {
        // Inclusive dates: an item returned on Friday is available again on Saturday.
        Long count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM booking WHERE equipment_id = ? AND status = 'CONFIRMED'
            AND pickup_date <= ? AND return_date >= ?
            """, Long.class, equipmentId, Date.valueOf(returns), Date.valueOf(pickup));
        return count != null && count > 0;
    }

    public BookingView createBooking(UserAccount teacher, long equipmentId, LocalDate pickup, LocalDate returns) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO booking (teacher_id, teacher_name, room, equipment_id, pickup_date, return_date)
                VALUES (?, ?, ?, ?, ?, ?)
                """, new String[]{"id"});
            statement.setLong(1, teacher.id());
            statement.setString(2, teacher.name());
            statement.setString(3, teacher.room());
            statement.setLong(4, equipmentId);
            statement.setDate(5, Date.valueOf(pickup));
            statement.setDate(6, Date.valueOf(returns));
            return statement;
        }, key);
        return booking(key.getKey().longValue(), teacher.id());
    }

    private RowMapper<BookingView> bookingMapper(long viewerId) {
        return (rs, row) -> new BookingView(rs.getLong("id"), rs.getString("teacher_name"), rs.getString("room"),
            rs.getLong("equipment_id"), rs.getString("equipment_name"), rs.getDate("pickup_date").toLocalDate(),
            rs.getDate("return_date").toLocalDate(), rs.getString("status"), rs.getLong("teacher_id") == viewerId);
    }

    public List<BookingView> bookings(long viewerId) {
        return jdbc.query("""
            SELECT b.*, e.name AS equipment_name FROM booking b JOIN equipment e ON e.id = b.equipment_id
            ORDER BY b.pickup_date DESC, b.id DESC
            """, bookingMapper(viewerId));
    }

    private BookingView booking(long id, long viewerId) {
        return jdbc.queryForObject("""
            SELECT b.*, e.name AS equipment_name FROM booking b JOIN equipment e ON e.id = b.equipment_id WHERE b.id = ?
            """, bookingMapper(viewerId), id);
    }
}
