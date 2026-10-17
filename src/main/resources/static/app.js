"use strict";

(() => {
  const page = document.body.dataset.page;
  const $ = (id) => document.getElementById(id);
  const state = {
    user: null,
    catalog: null,
    bookings: [],
    teachers: [],
    csrf: null,
    csrfPromise: null,
    pendingRemoval: null,
  };

  class ApiError extends Error {
    constructor(message, status = 0) {
      super(message);
      this.status = status;
    }
  }

  function showMessage(id, message, type = "success") {
    const element = $(id);
    if (!element) return;
    element.textContent = message;
    element.className = `notice ${type === "success" ? "" : type}`.trim();
    element.hidden = false;
  }

  function clearMessage(id) {
    const element = $(id);
    if (element) {
      element.hidden = true;
      element.textContent = "";
    }
  }

  function handleError(id, error) {
    if (error.status === 401 && (page === "bookings" || page === "secretary")) {
      window.location.replace("/login.html?expirou=1");
      return;
    }
    showMessage(
      id,
      error.message || "Não foi possível concluir a ação. Tente novamente.",
      "error",
    );
  }

  function busy(button, loading, label = "Aguarde…") {
    if (!button) return;
    if (loading) {
      button.dataset.originalText = button.textContent;
      button.disabled = true;
      button.textContent = label;
    } else {
      if (button.dataset.originalText)
        button.textContent = button.dataset.originalText;
      button.disabled = false;
      delete button.dataset.originalText;
    }
  }

  async function getCsrf(force = false) {
    if (force) {
      state.csrf = null;
      state.csrfPromise = null;
    }
    if (state.csrf) return state.csrf;
    if (!state.csrfPromise) {
      state.csrfPromise = fetch("/api/csrf", {
        credentials: "same-origin",
        headers: { Accept: "application/json" },
        cache: "no-store",
      })
        .catch(() => {
          throw new ApiError(
            "Não foi possível conectar ao sistema. Confira sua conexão e tente novamente.",
          );
        })
        .then(async (response) => {
          if (!response.ok)
            throw new ApiError(
              "Não foi possível preparar o acesso. Recarregue a página e tente novamente.",
              response.status,
            );
          const data = await response.json();
          if (!data.token || !data.headerName)
            throw new ApiError(
              "Não foi possível preparar o acesso. Recarregue a página.",
            );
          state.csrf = data;
          return data;
        })
        .finally(() => {
          state.csrfPromise = null;
        });
    }
    return state.csrfPromise;
  }

  async function api(path, options = {}) {
    const method = options.method || "GET";
    const headers = { Accept: "application/json" };
    if (options.body !== undefined)
      headers["Content-Type"] = "application/json";
    if (method !== "GET" && method !== "HEAD") {
      const csrf = await getCsrf();
      headers[csrf.headerName] = csrf.token;
    }
    let response;
    try {
      response = await fetch(path, {
        method,
        headers,
        credentials: "same-origin",
        cache: "no-store",
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch {
      throw new ApiError(
        "Não foi possível conectar ao sistema. Confira sua conexão e tente novamente.",
      );
    }
    const text = await response.text();
    let data = null;
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        /* Um erro do servidor pode não conter JSON. */
      }
    }
    if (!response.ok) {
      throw new ApiError(
        data?.message ||
          (response.status === 403
            ? "Você não tem permissão para realizar esta ação. Atualize a página e tente novamente."
            : "Não foi possível concluir a ação. Tente novamente."),
        response.status,
      );
    }
    return data;
  }

  function today() {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: "America/Sao_Paulo",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date());
    const value = (type) => parts.find((part) => part.type === type).value;
    return `${value("year")}-${value("month")}-${value("day")}`;
  }

  function dateLabel(value) {
    if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return "—";
    const [year, month, day] = value.split("-");
    return `${day}/${month}/${year}`;
  }

  function normalize(value) {
    return String(value ?? "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
  }
  function initials(name) {
    return (
      String(name || "")
        .trim()
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0])
        .join("")
        .toUpperCase() || "TZ"
    );
  }
  function element(tag, className = "", text = "") {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
  }
  function option(value, label) {
    const node = element("option", "", label);
    node.value = String(value);
    return node;
  }
  function destination(user) {
    return user.role === "SECRETARY"
      ? "/secretaria.html"
      : "/agendamentos.html";
  }

  function setupPasswordButtons() {
    document.querySelectorAll("[data-password-target]").forEach((button) => {
      button.addEventListener("click", () => {
        const input = $(button.dataset.passwordTarget);
        const visible = input.type === "password";
        input.type = visible ? "text" : "password";
        button.textContent = visible ? "Ocultar" : "Ver";
        button.setAttribute("aria-pressed", String(visible));
        button.setAttribute(
          "aria-label",
          `${visible ? "Ocultar" : "Mostrar"} ${input.id === "confirm-password" ? "confirmação de senha" : "senha"}`,
        );
      });
    });
  }

  function setupLogin() {
    const params = new URLSearchParams(window.location.search);
    if (params.get("perfil") === "secretaria") {
      $("login-eyebrow").textContent = "ACESSO DA SECRETARIA";
      $("login-intro").textContent =
        "Entre com a conta da secretaria para gerenciar os docentes.";
      $("signup-link").hidden = true;
      $("login-footnote").textContent =
        "Este acesso é exclusivo da secretaria da escola.";
    }
    if (params.get("cadastro") === "sucesso")
      showMessage(
        "form-message",
        "Sua conta foi criada! Entre com seu e-mail e senha. A secretaria precisa definir sua sala antes do primeiro agendamento.",
      );
    else if (params.get("expirou") === "1")
      showMessage(
        "form-message",
        "Sua sessão terminou. Entre novamente para continuar.",
        "info",
      );
    else if (params.get("saiu") === "1")
      showMessage("form-message", "Você saiu da sua conta.", "info");
    $("login-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const button = event.currentTarget.querySelector('[type="submit"]');
      clearMessage("form-message");
      busy(button, true, "Entrando…");
      try {
        const user = await api("/api/auth/login", {
          method: "POST",
          body: {
            email: $("email").value.trim(),
            password: $("password").value,
          },
        });
        await getCsrf(true);
        window.location.assign(destination(user));
      } catch (error) {
        handleError("form-message", error);
        busy(button, false);
      }
    });
  }

  function setupRegister() {
    const confirm = $("confirm-password");
    function validatePasswords() {
      confirm.setCustomValidity(
        confirm.value && confirm.value !== $("password").value
          ? "As senhas precisam ser iguais."
          : "",
      );
    }
    confirm.addEventListener("input", validatePasswords);
    $("password").addEventListener("input", validatePasswords);
    $("register-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      validatePasswords();
      if (!event.currentTarget.reportValidity()) return;
      const button = event.currentTarget.querySelector('[type="submit"]');
      clearMessage("form-message");
      busy(button, true, "Criando sua conta…");
      try {
        await api("/api/auth/register", {
          method: "POST",
          body: {
            name: $("name").value.trim(),
            email: $("email").value.trim(),
            password: $("password").value,
          },
        });
        window.location.assign("/login.html?cadastro=sucesso");
      } catch (error) {
        handleError("form-message", error);
        busy(button, false);
      }
    });
  }

  function displayUser() {
    const user = state.user;
    if (!user) return;
    $("user-name").textContent = user.name;
    $("user-initials").textContent = initials(user.name);
    document.querySelectorAll("[data-secretary-only]").forEach((node) => {
      node.hidden = user.role !== "SECRETARY";
    });
    if (page === "bookings") {
      const secretary = user.role === "SECRETARY";
      const roomReady = Boolean(user.room) && user.active !== false;
      $("greeting").textContent = secretary
        ? "A agenda da nossa escola."
        : `Olá, ${user.name.trim().split(/\s+/)[0]}!`;
      $("greeting-description").textContent = secretary
        ? "Consulte o uso dos equipamentos pelos docentes."
        : "Uma boa aula começa com um bom planejamento.";
      $("room-badge").textContent = secretary
        ? "Secretaria"
        : user.room
          ? `Sua sala: ${user.room}`
          : "Aguardando sala";
      $("room-badge").classList.toggle("pending", !secretary && !user.room);
      $("pending-notice").hidden = secretary || roomReady;
      $("reservation-panel").hidden = secretary;
      $("booking-fields").disabled = !roomReady || secretary || !state.catalog;
      if (secretary) {
        const option = $("booking-filter").querySelector('[value="mine"]');
        if (option) option.remove();
        $("mine-count").parentElement.querySelector("span").textContent =
          "Reservas em uso hoje";
      }
    }
  }

  function setupLogout() {
    document.querySelectorAll("[data-logout]").forEach((button) => {
      button.addEventListener("click", async () => {
        busy(button, true, "Saindo…");
        try {
          await api("/api/auth/logout", { method: "POST" });
          state.csrf = null;
          window.location.replace("/login.html?saiu=1");
        } catch (error) {
          handleError("page-message", error);
          busy(button, false);
        }
      });
    });
  }

  function populateCatalog() {
    if (page === "bookings") {
      $("equipment").replaceChildren(option("", "Selecione o equipamento"));
      state.catalog.equipment.forEach((item) =>
        $("equipment").append(option(item.id, item.name)),
      );
      $("equipment-count").textContent = String(
        state.catalog.equipment.length,
      ).padStart(2, "0");
    } else {
      $("room").replaceChildren(option("", "Selecione a sala"));
      state.catalog.rooms.forEach((room) =>
        $("room").append(option(room, room)),
      );
      $("rooms-count").textContent = String(
        state.catalog.rooms.length,
      ).padStart(2, "0");
    }
  }

  function currentBookings() {
    const currentDay = today();
    return state.bookings.filter(
      (booking) =>
        booking.status === "CONFIRMED" && booking.returnDate >= currentDay,
    );
  }

  function bookingStatus(booking) {
    if (booking.status === "CANCELLED")
      return { label: "Cancelada", className: "cancelled" };
    const currentDay = today();
    if (booking.returnDate < currentDay)
      return { label: "Concluída", className: "finished" };
    if (booking.pickupDate > currentDay)
      return { label: "Agendada", className: "pending" };
    return { label: "Em uso", className: "" };
  }

  function emptyTable(body, cols, title, description) {
    const row = element("tr");
    const cell = element("td", "table-state");
    cell.colSpan = cols;
    cell.append(element("strong", "", title), element("span", "", description));
    row.append(cell);
    body.replaceChildren(row);
  }

  function renderBookings() {
    if (!state.user) return;
    const query = normalize($("booking-search").value.trim());
    const filter = $("booking-filter").value;
    const currentDay = today();
    const current = currentBookings();
    $("school-count").textContent = String(current.length).padStart(2, "0");
    $("mine-count").textContent = String(
      state.user.role === "SECRETARY"
        ? current.filter((booking) => booking.pickupDate <= currentDay).length
        : current.filter((booking) => booking.mine).length,
    ).padStart(2, "0");
    const filtered = state.bookings
      .filter((booking) => {
        if (
          filter === "current" &&
          (booking.status !== "CONFIRMED" || booking.returnDate < currentDay)
        )
          return false;
        if (filter === "mine" && !booking.mine) return false;
        return normalize(
          `${booking.teacherName} ${booking.room} ${booking.equipmentName}`,
        ).includes(query);
      })
      .sort((a, b) =>
        filter === "all" || filter === "mine"
          ? b.pickupDate.localeCompare(a.pickupDate) || b.id - a.id
          : a.pickupDate.localeCompare(b.pickupDate) || a.id - b.id,
      );
    $("bookings-total").textContent =
      `${filtered.length} ${filtered.length === 1 ? "reserva encontrada" : "reservas encontradas"}`;
    const body = $("bookings-table");
    if (!filtered.length) {
      emptyTable(
        body,
        6,
        query
          ? "Nenhuma reserva encontrada."
          : filter === "mine"
            ? "Você ainda não tem reservas."
            : "A agenda está livre por aqui.",
        query
          ? "Tente buscar outro nome, sala ou equipamento."
          : filter === "current"
            ? "As reservas atuais e futuras aparecerão aqui."
            : "Os agendamentos aparecerão nesta tabela.",
      );
      return;
    }
    const fragment = document.createDocumentFragment();
    filtered.forEach((booking) => {
      const row = element("tr");
      const teacher = element("td", "teacher-name");
      const name = element("span", "teacher-cell");
      name.append(
        element("span", "table-avatar", initials(booking.teacherName)),
        element("span", "", booking.teacherName),
      );
      teacher.append(name);
      const room = element("td");
      room.append(element("span", "room-chip", booking.room || "—"));
      const status = bookingStatus(booking);
      const statusCell = element("td");
      statusCell.append(
        element("span", `status ${status.className}`.trim(), status.label),
      );
      row.append(
        teacher,
        room,
        element("td", "", booking.equipmentName),
        element("td", "", dateLabel(booking.pickupDate)),
        element("td", "", dateLabel(booking.returnDate)),
        statusCell,
      );
      fragment.append(row);
    });
    body.replaceChildren(fragment);
  }

  async function loadBookings(refreshUser = false) {
    if (refreshUser) {
      const [user, bookings] = await Promise.all([
        api("/api/auth/me"),
        api("/api/bookings"),
      ]);
      state.user = user;
      state.bookings = bookings;
      displayUser();
    } else state.bookings = await api("/api/bookings");
    renderBookings();
  }

  function setupBookings() {
    const currentDay = today();
    $("pickup-date").min = currentDay;
    $("return-date").min = currentDay;
    $("pickup-date").addEventListener("change", () => {
      $("return-date").min = $("pickup-date").value || today();
      $("return-date").setCustomValidity("");
      if (
        $("return-date").value &&
        $("return-date").value < $("pickup-date").value
      )
        $("return-date").value = "";
    });
    $("return-date").addEventListener("change", () =>
      $("return-date").setCustomValidity(""),
    );
    $("booking-form").addEventListener("submit", async (event) => {
      event.preventDefault();
      if (!state.user?.room || state.user.role !== "TEACHER") return;
      const pickupDate = $("pickup-date").value;
      const returnDate = $("return-date").value;
      if (returnDate < pickupDate) {
        $("return-date").setCustomValidity(
          "A devolução deve ser no mesmo dia ou depois da retirada.",
        );
        $("return-date").reportValidity();
        return;
      }
      const button = event.currentTarget.querySelector('[type="submit"]');
      clearMessage("booking-message");
      busy(button, true, "Confirmando…");
      try {
        await api("/api/bookings", {
          method: "POST",
          body: {
            equipmentId: Number($("equipment").value),
            pickupDate,
            returnDate,
          },
        });
        showMessage(
          "booking-message",
          "Reserva confirmada! Seu agendamento já está na agenda da escola.",
        );
        event.target.reset();
        $("return-date").min = today();
        try {
          await loadBookings();
        } catch (error) {
          handleError("page-message", error);
        }
      } catch (error) {
        handleError("booking-message", error);
      } finally {
        busy(button, false);
      }
    });
    $("booking-search").addEventListener("input", renderBookings);
    $("booking-filter").addEventListener("change", renderBookings);
    $("refresh-bookings").addEventListener("click", async (event) => {
      const button = event.currentTarget;
      clearMessage("page-message");
      busy(button, true, "Atualizando…");
      try {
        await loadBookings(true);
      } catch (error) {
        handleError("page-message", error);
      } finally {
        busy(button, false);
      }
    });
  }

  function selectedTeacher() {
    return state.teachers.find(
      (teacher) => String(teacher.id) === $("teacher").value && teacher.active,
    );
  }

  function updateAssignmentActions() {
    const teacher = selectedTeacher();
    const room = $("room").value;
    $("assign-teacher").disabled = !teacher || Boolean(teacher.room) || !room;
    $("change-room").disabled =
      !teacher || !teacher.room || !room || teacher.room === room;
    $("remove-teacher").disabled = !teacher;
    $("teacher-selection-info").textContent = !teacher
      ? "Selecione um docente para começar."
      : teacher.room
        ? `Sala atual de ${teacher.name}: ${teacher.room}. Selecione outra sala para alterar.`
        : `${teacher.name} está aguardando a definição de uma sala.`;
  }

  function renderTeachers() {
    const selected = $("teacher").value;
    const active = state.teachers.filter((teacher) => teacher.active);
    $("active-count").textContent = String(active.length).padStart(2, "0");
    $("pending-count").textContent = String(
      active.filter((teacher) => !teacher.room).length,
    ).padStart(2, "0");
    $("teacher").replaceChildren(
      option(
        "",
        active.length
          ? "Selecione o docente"
          : "Nenhum docente cadastrado",
      ),
    );
    active
      .sort(
        (a, b) =>
          Number(Boolean(a.room)) - Number(Boolean(b.room)) ||
          a.name.localeCompare(b.name, "pt-BR"),
      )
      .forEach((teacher) =>
        $("teacher").append(
          option(
            teacher.id,
            `${teacher.name}${teacher.room ? ` · ${teacher.room}` : " · Aguardando sala"}`,
          ),
        ),
      );
    if (active.some((teacher) => String(teacher.id) === selected))
      $("teacher").value = selected;
    else $("room").value = "";
    $("assignment-fields").disabled = !state.catalog;
    updateAssignmentActions();
    renderTeacherTable();
  }

  function renderTeacherTable() {
    const query = normalize($("teacher-search").value.trim());
    const filter = $("teacher-filter").value;
    const filtered = state.teachers
      .filter((teacher) => {
        if (filter === "active" && !teacher.active) return false;
        if (filter === "pending" && (!teacher.active || teacher.room))
          return false;
        if (filter === "inactive" && teacher.active) return false;
        return normalize(
          `${teacher.name} ${teacher.email} ${teacher.room || ""}`,
        ).includes(query);
      })
      .sort(
        (a, b) =>
          Number(Boolean(a.room)) - Number(Boolean(b.room)) ||
          a.name.localeCompare(b.name, "pt-BR"),
      );
    $("teachers-total").textContent =
      `${filtered.length} ${filtered.length === 1 ? "docente encontrado" : "docentes encontrados"}`;
    const body = $("teachers-table");
    if (!filtered.length) {
      emptyTable(
        body,
        5,
        query
          ? "Nenhum docente encontrado."
          : filter === "pending"
            ? "Todas as salas estão em dia."
            : filter === "inactive"
              ? "Nenhuma conta removida."
              : "Vamos conhecer a nossa equipe.",
        query
          ? "Tente buscar outro nome, e-mail ou sala."
          : filter === "pending"
            ? "Não há docentes aguardando definição de sala."
            : filter === "inactive"
              ? "Contas removidas aparecerão aqui."
              : "Os docentes aparecerão aqui depois de criarem suas contas.",
      );
      return;
    }
    const fragment = document.createDocumentFragment();
    filtered.forEach((teacher) => {
      const row = element("tr");
      const name = element("td", "teacher-name");
      const content = element("span", "teacher-cell");
      content.append(
        element("span", "table-avatar", initials(teacher.name)),
        element("span", "", teacher.name),
      );
      name.append(content);
      const room = element("td");
      room.append(element("span", "room-chip", teacher.room || "—"));
      const status = element("td");
      status.append(
        element(
          "span",
          `status ${!teacher.active ? "cancelled" : !teacher.room ? "pending" : ""}`.trim(),
          !teacher.active
            ? "Removida"
            : !teacher.room
              ? "Aguardando sala"
              : "Vinculada",
        ),
      );
      const actions = element("td");
      if (teacher.active) {
        const button = element("button", "row-button", "Selecionar");
        button.type = "button";
        button.setAttribute("aria-label", `Selecionar ${teacher.name}`);
        button.addEventListener("click", () => {
          $("teacher").value = String(teacher.id);
          $("room").value = teacher.room || "";
          updateAssignmentActions();
          $("assignment-heading").scrollIntoView({
            behavior: window.matchMedia("(prefers-reduced-motion: reduce)")
              .matches
              ? "auto"
              : "smooth",
            block: "start",
          });
          $("teacher").focus({ preventScroll: true });
        });
        actions.append(button);
      } else actions.textContent = "—";
      row.append(name, element("td", "", teacher.email), room, status, actions);
      fragment.append(row);
    });
    body.replaceChildren(fragment);
  }

  async function loadTeachers() {
    state.teachers = await api("/api/teachers");
    renderTeachers();
  }

  async function assignTeacher(button) {
    const teacher = selectedTeacher();
    const room = $("room").value;
    if (!teacher || !room || !$("assignment-form").reportValidity()) return;
    const isChange = Boolean(teacher.room);
    clearMessage("assignment-message");
    busy(button, true, "Salvando…");
    $("assignment-fields").disabled = true;
    try {
      await api(`/api/teachers/${teacher.id}/assignment`, {
        method: "POST",
        body: { room },
      });
      showMessage(
        "assignment-message",
        `${isChange ? "Sala atualizada" : "Professora vinculada"}! ${teacher.name} agora está na sala ${room}.`,
      );
      try {
        await loadTeachers();
      } catch (error) {
        handleError("page-message", error);
      }
    } catch (error) {
      handleError("assignment-message", error);
    } finally {
      busy(button, false);
      $("assignment-fields").disabled = false;
      updateAssignmentActions();
    }
  }

  function setupSecretary() {
    $("teacher").addEventListener("change", () => {
      clearMessage("assignment-message");
      $("room").value = selectedTeacher()?.room || "";
      updateAssignmentActions();
    });
    $("room").addEventListener("change", updateAssignmentActions);
    $("assignment-form").addEventListener("submit", (event) => {
      event.preventDefault();
      assignTeacher($("assign-teacher"));
    });
    $("change-room").addEventListener("click", (event) =>
      assignTeacher(event.currentTarget),
    );
    $("teacher-search").addEventListener("input", renderTeacherTable);
    $("teacher-filter").addEventListener("change", renderTeacherTable);
    $("refresh-teachers").addEventListener("click", async (event) => {
      const button = event.currentTarget;
      clearMessage("page-message");
      busy(button, true, "Atualizando…");
      try {
        await loadTeachers();
      } catch (error) {
        handleError("page-message", error);
      } finally {
        busy(button, false);
      }
    });
    const dialog = $("remove-dialog");
    $("remove-teacher").addEventListener("click", () => {
      const teacher = selectedTeacher();
      if (!teacher) return;
      state.pendingRemoval = teacher;
      $("remove-name").textContent = teacher.name;
      dialog.showModal();
      $("cancel-removal").focus();
    });
    $("cancel-removal").addEventListener("click", () => dialog.close());
    dialog.addEventListener("close", () => {
      state.pendingRemoval = null;
    });
    dialog.addEventListener("cancel", (event) => {
      if ($("confirm-removal").disabled) event.preventDefault();
    });
    $("confirm-removal").addEventListener("click", async (event) => {
      const teacher = state.pendingRemoval;
      if (!teacher) return;
      const button = event.currentTarget;
      clearMessage("assignment-message");
      busy(button, true, "Removendo…");
      $("cancel-removal").disabled = true;
      try {
        await api(`/api/teachers/${teacher.id}`, { method: "DELETE" });
        dialog.close();
        showMessage(
          "assignment-message",
          `O acesso de ${teacher.name} foi removido. As reservas atuais e futuras foram canceladas e o histórico foi preservado.`,
        );
        $("teacher").value = "";
        $("room").value = "";
        try {
          await loadTeachers();
        } catch (error) {
          handleError("page-message", error);
        }
      } catch (error) {
        dialog.close();
        handleError("assignment-message", error);
      } finally {
        busy(button, false);
        $("cancel-removal").disabled = false;
        updateAssignmentActions();
      }
    });
  }

  async function startDashboard() {
    setupLogout();
    if (page === "bookings") setupBookings();
    else setupSecretary();
    try {
      [state.user] = await Promise.all([api("/api/auth/me"), getCsrf()]);
      if (page === "secretary" && state.user.role !== "SECRETARY") {
        window.location.replace("/agendamentos.html");
        return;
      }
      displayUser();
      const [catalog, data] = await Promise.all([
        api("/api/catalog"),
        api(page === "bookings" ? "/api/bookings" : "/api/teachers"),
      ]);
      state.catalog = catalog;
      populateCatalog();
      if (page === "bookings") {
        state.bookings = data;
        displayUser();
        renderBookings();
      } else {
        state.teachers = data;
        renderTeachers();
      }
    } catch (error) {
      handleError("page-message", error);
      const body =
        page === "bookings" ? $("bookings-table") : $("teachers-table");
      emptyTable(
        body,
        page === "bookings" ? 6 : 5,
        "Não foi possível carregar os dados.",
        "Recarregue a página para tentar novamente.",
      );
      const total =
        page === "bookings" ? $("bookings-total") : $("teachers-total");
      total.textContent = "Dados indisponíveis";
    }
  }

  setupPasswordButtons();
  if (page === "login" || page === "register") {
    if (page === "login") setupLogin();
    else setupRegister();
    getCsrf().catch((error) => handleError("form-message", error));
  } else if (page === "bookings" || page === "secretary") startDashboard();
})();
