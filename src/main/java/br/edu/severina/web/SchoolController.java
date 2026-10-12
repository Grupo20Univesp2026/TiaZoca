package br.edu.severina.web;

import br.edu.severina.domain.BookingView;
import br.edu.severina.domain.Catalog;
import br.edu.severina.domain.UserAccount;
import br.edu.severina.security.SchoolPrincipal;
import br.edu.severina.service.SchoolService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SchoolController {
    private final SchoolService service;
    public SchoolController(SchoolService service) { this.service = service; }

    public record BookingRequest(
        @NotNull(message = "Selecione um equipamento.") @Positive(message = "Selecione um equipamento válido.") Long equipmentId,
        @NotNull(message = "Informe a data de retirada.") LocalDate pickupDate,
        @NotNull(message = "Informe a data de devolução.") LocalDate returnDate) {}
    public record AssignmentRequest(@NotBlank(message = "Selecione uma sala.") String room) {}

    @GetMapping("/catalog") public Catalog.View catalog() { return service.catalog(); }

    @GetMapping("/bookings") public List<BookingView> bookings(@AuthenticationPrincipal SchoolPrincipal principal) {
        return service.bookings(principal.id());
    }

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingView book(@Valid @RequestBody BookingRequest body, @AuthenticationPrincipal SchoolPrincipal principal) {
        return service.book(principal.id(), body.equipmentId(), body.pickupDate(), body.returnDate());
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasRole('SECRETARY')")
    public List<UserAccount.UserView> teachers() { return service.teachers(); }

    @PostMapping("/teachers/{id}/assignment")
    @PreAuthorize("hasRole('SECRETARY')")
    public UserAccount.UserView assign(@PathVariable long id, @Valid @RequestBody AssignmentRequest body) {
        return service.assignRoom(id, body.room());
    }

    @DeleteMapping("/teachers/{id}")
    @PreAuthorize("hasRole('SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id) { service.removeTeacher(id); }
}
