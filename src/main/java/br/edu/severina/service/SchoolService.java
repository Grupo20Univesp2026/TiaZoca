package br.edu.severina.service;

import br.edu.severina.domain.ApiException;
import br.edu.severina.domain.BookingView;
import br.edu.severina.domain.Catalog;
import br.edu.severina.domain.UserAccount;
import br.edu.severina.repository.SchoolRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class SchoolService {
    private final SchoolRepository repository;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public SchoolService(SchoolRepository repository, PasswordEncoder encoder, Clock clock) {
        this.repository = repository;
        this.encoder = encoder;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public static void validatePassword(String password) {
        if (password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A senha deve ter ao menos 8 caracteres e no máximo 72 bytes.");
        }
    }

    @Transactional
    public void register(String name, String email, String password) {
        validatePassword(password);
        try {
            repository.createUser(name.strip(), normalizeEmail(email), encoder.encode(password), "TEACHER");
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "Este e-mail já está cadastrado. Entre com a sua conta.");
        }
    }

    public UserAccount currentUser(long id) {
        UserAccount user = repository.findUser(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar."));
        if (!user.active()) throw new ApiException(HttpStatus.UNAUTHORIZED, "Seu acesso foi removido pela secretaria.");
        return user;
    }

    public Catalog.View catalog() {
        return new Catalog.View(Catalog.ROOMS, repository.equipment());
    }

    public List<UserAccount.UserView> teachers() {
        return repository.teachers().stream().map(UserAccount::view).toList();
    }

    @Transactional
    public UserAccount.UserView assignRoom(long id, String room) {
        if (!Catalog.ROOMS.contains(room)) throw new ApiException(HttpStatus.BAD_REQUEST, "Selecione uma sala válida.");
        teacherForChange(id);
        return repository.assignRoom(id, room).view();
    }

    @Transactional
    public void removeTeacher(long id) {
        teacherForChange(id);
        repository.deactivateTeacher(id, LocalDate.now(clock));
    }

    private UserAccount teacherForChange(long id) {
        UserAccount user = repository.lockUser(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Professora não encontrada."));
        if (!"TEACHER".equals(user.role()) || !user.active()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Professora não encontrada.");
        }
        return user;
    }

    public List<BookingView> bookings(long viewerId) { return repository.bookings(viewerId); }

    @Transactional
    public BookingView book(long teacherId, long equipmentId, LocalDate pickup, LocalDate returns) {
        UserAccount teacher = teacherForChange(teacherId);
        if (teacher.room() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Aguarde a secretaria definir sua sala antes de agendar.");
        }
        if (pickup.isBefore(LocalDate.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A data de retirada não pode estar no passado.");
        }
        if (returns.isBefore(pickup)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A devolução deve ocorrer na data da retirada ou depois dela.");
        }
        repository.lockEquipment(equipmentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Equipamento não encontrado."));
        if (repository.hasOverlap(equipmentId, pickup, returns)) {
            throw new ApiException(HttpStatus.CONFLICT, "Este equipamento já está reservado nesse período. Escolha outras datas ou outro equipamento.");
        }
        return repository.createBooking(teacher, equipmentId, pickup, returns);
    }
}
