package br.edu.severina;

import br.edu.severina.domain.ApiException;
import br.edu.severina.repository.SchoolRepository;
import br.edu.severina.service.SchoolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:severina-tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
    "spring.datasource.username=${TEST_DB_USERNAME:sa}", "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "app.secretary.email=secretaria@example.test", "app.secretary.password=SecretariaTeste123", "app.secretary.name=Secretaria de teste"
})
@AutoConfigureMockMvc
@Import(SchoolIntegrationTest.FixedTime.class)
class SchoolIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SchoolService service;
    @Autowired SchoolRepository repository;
    private static final String PASSWORD = "ProfessoraTeste123";

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-12T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @BeforeEach void cleanData() {
        jdbc.update("DELETE FROM booking");
        jdbc.update("DELETE FROM app_user WHERE role = 'TEACHER'");
    }

    @Test void unauthenticatedAndCsrfProtected() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").exists());
        mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("name", "Ana", "email", "ana@example.test", "password", PASSWORD))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/secretaria.html")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html"));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void fullTeacherAndSecretaryWorkflow() throws Exception {
        MockHttpSession teacher = registerAndLogin("Ana Maria", "ana@example.test");
        long teacherId = current(teacher).path("id").asLong();
        assertThat(current(teacher).path("room").isNull()).isTrue();
        mvc.perform(get("/api/catalog").session(teacher)).andExpect(status().isOk())
            .andExpect(jsonPath("$.rooms.length()").value(9)).andExpect(jsonPath("$.equipment.length()").value(7));
        postJson("/api/bookings", teacher, bookingBody(1, "2026-10-12", "2026-10-12"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Aguarde a secretaria definir sua sala antes de agendar."));
        mvc.perform(get("/api/teachers").session(teacher)).andExpect(status().isForbidden());
        postJson("/api/teachers/" + teacherId + "/assignment", teacher, Map.of("room", "PPA")).andExpect(status().isForbidden());

        MockHttpSession secretary = login("secretaria@example.test", "SecretariaTeste123");
        postJson("/api/teachers/" + teacherId + "/assignment", secretary, Map.of("room", "INVALID")).andExpect(status().isBadRequest());
        postJson("/api/teachers/" + teacherId + "/assignment", secretary, Map.of("room", "PPA"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.room").value("PPA"));
        assertThat(current(teacher).path("room").asText()).isEqualTo("PPA");
        postJson("/api/bookings", teacher, bookingBody(1, "2026-10-11", "2026-10-12")).andExpect(status().isBadRequest());
        postJson("/api/bookings", teacher, bookingBody(1, "2026-10-14", "2026-10-13")).andExpect(status().isBadRequest());
        postJson("/api/bookings", teacher, bookingBody(1, "2026-10-12", "2026-10-13"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.room").value("PPA"))
            .andExpect(jsonPath("$.teacherName").value("Ana Maria")).andExpect(jsonPath("$.mine").value(true));

        MockHttpSession secondTeacher = registerAndLogin("Beatriz", "bia@example.test");
        long secondId = current(secondTeacher).path("id").asLong();
        postJson("/api/teachers/" + secondId + "/assignment", secretary, Map.of("room", "M1B")).andExpect(status().isOk());
        postJson("/api/bookings", secondTeacher, bookingBody(1, "2026-10-13", "2026-10-13")).andExpect(status().isConflict());
        postJson("/api/bookings", secondTeacher, bookingBody(2, "2026-10-12", "2026-10-12")).andExpect(status().isCreated());
        postJson("/api/bookings", secondTeacher, bookingBody(1, "2026-10-14", "2026-10-14")).andExpect(status().isCreated());
        postJson("/api/bookings", secretary, bookingBody(3, "2026-10-12", "2026-10-12")).andExpect(status().isForbidden());

        postJson("/api/teachers/" + teacherId + "/assignment", secretary, Map.of("room", "M2C")).andExpect(status().isOk());
        assertThat(current(teacher).path("room").asText()).isEqualTo("M2C");
        mvc.perform(get("/api/bookings").session(teacher)).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.teacherName == 'Ana Maria')].room").value("PPA"));
        Csrf csrf = csrf(secretary);
        mvc.perform(delete("/api/teachers/" + teacherId).session(secretary).header(csrf.header(), csrf.token()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").session(teacher)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/teachers").session(secretary)).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.name == 'Ana Maria')].active").value(false));
        mvc.perform(get("/api/bookings").session(secretary)).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.teacherName == 'Ana Maria')].status").value("CANCELLED"));
        postJson("/api/bookings", secondTeacher, bookingBody(1, "2026-10-12", "2026-10-13")).andExpect(status().isCreated());
        MockHttpSession removedLogin = new MockHttpSession();
        postJson("/api/auth/login", removedLogin, Map.of("email", "ana@example.test", "password", PASSWORD)).andExpect(status().isUnauthorized());

        Csrf logoutCsrf = csrf(secondTeacher);
        mvc.perform(post("/api/auth/logout").session(secondTeacher).header(logoutCsrf.header(), logoutCsrf.token()))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void registrationNormalizesEmailAndRejectsDuplicateAndInvalidData() throws Exception {
        MockHttpSession session = new MockHttpSession();
        postJson("/api/auth/register", session, Map.of("name", "Ana", "email", "ANA@example.test", "password", PASSWORD))
            .andExpect(status().isCreated());
        postJson("/api/auth/register", session, Map.of("name", "Outra", "email", "ana@example.test", "password", PASSWORD))
            .andExpect(status().isConflict());
        postJson("/api/auth/register", session, Map.of("name", "", "email", "other@example.test", "password", PASSWORD))
            .andExpect(status().isBadRequest());
        postJson("/api/auth/register", session, Map.of("name", "Ana", "email", "invalido", "password", "curta"))
            .andExpect(status().isBadRequest());
        postJson("/api/auth/register", session, Map.of("name", "Ana", "email", "utf8@example.test", "password", "á".repeat(40)))
            .andExpect(status().isBadRequest());
        postJson("/api/auth/register", session, Map.of("name", "Ana", "email", "role@example.test", "password", PASSWORD, "role", "SECRETARY"))
            .andExpect(status().isBadRequest());
        MockHttpSession teacher = login("ana@example.test", PASSWORD);
        assertThat(current(teacher).path("role").asText()).isEqualTo("TEACHER");
        String storedHash = repository.findUserByEmail("ana@example.test").orElseThrow().passwordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test void simultaneousRequestsCannotBookTheSameEquipment() throws Exception {
        service.register("Ana", "race-ana@example.test", PASSWORD);
        service.register("Bia", "race-bia@example.test", PASSWORD);
        long ana = repository.findUserByEmail("race-ana@example.test").orElseThrow().id();
        long bia = repository.findUserByEmail("race-bia@example.test").orElseThrow().id();
        service.assignRoom(ana, "PPA");
        service.assignRoom(bia, "PPB");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> raceBook(ana, ready, start));
            var second = executor.submit(() -> raceBook(bia, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int successCount = (first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertThat(successCount).isEqualTo(1);
        }
        assertThat(repository.bookings(ana)).hasSize(1);
    }

    @Test void removalPreservesFinishedHistoryAndCancelsCurrentAndFutureBookings() {
        service.register("Ana", "historico@example.test", PASSWORD);
        long id = repository.findUserByEmail("historico@example.test").orElseThrow().id();
        service.assignRoom(id, "PPC");
        jdbc.update("""
            INSERT INTO booking (teacher_id, teacher_name, room, equipment_id, pickup_date, return_date)
            VALUES (?, 'Ana', 'PPC', 1, DATE '2026-10-10', DATE '2026-10-11')
            """, id);
        service.book(id, 2, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
        service.book(id, 3, LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
        service.removeTeacher(id);
        var history = repository.bookings(id);
        assertThat(history).hasSize(3);
        assertThat(history.stream().filter(booking -> booking.status().equals("CONFIRMED"))).hasSize(1);
        assertThat(history.stream().filter(booking -> booking.status().equals("CANCELLED"))).hasSize(2);
        assertThat(history).allMatch(booking -> booking.teacherName().equals("Ana") && booking.room().equals("PPC"));
        assertThat(repository.findUser(id).orElseThrow().active()).isFalse();
    }

    private boolean raceBook(long id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Race test timed out.");
        try {
            service.book(id, 7, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12));
            return true;
        } catch (ApiException exception) {
            assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
            return false;
        }
    }

    private MockHttpSession registerAndLogin(String name, String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        postJson("/api/auth/register", session, Map.of("name", name, "email", email, "password", PASSWORD))
            .andExpect(status().isCreated());
        MvcResult result = postJson("/api/auth/login", session, Map.of("email", email, "password", PASSWORD))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = postJson("/api/auth/login", new MockHttpSession(), Map.of("email", email, "password", password))
            .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private JsonNode current(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String path, MockHttpSession session, Map<String, ?> body) throws Exception {
        Csrf csrf = csrf(session);
        return mvc.perform(post(path).session(session).header(csrf.header(), csrf.token()).contentType("application/json")
            .content(json.writeValueAsString(body)));
    }

    private Csrf csrf(MockHttpSession session) throws Exception {
        MvcResult result = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        JsonNode token = json.readTree(result.getResponse().getContentAsString());
        return new Csrf(token.path("headerName").asText(), token.path("token").asText());
    }

    private Map<String, Object> bookingBody(long equipmentId, String pickup, String returns) {
        return Map.of("equipmentId", equipmentId, "pickupDate", pickup, "returnDate", returns);
    }
    private record Csrf(String header, String token) {}
}
