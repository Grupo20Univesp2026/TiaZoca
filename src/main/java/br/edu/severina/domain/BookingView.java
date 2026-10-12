package br.edu.severina.domain;

import java.time.LocalDate;

public record BookingView(long id, String teacherName, String room, long equipmentId,
                          String equipmentName, LocalDate pickupDate, LocalDate returnDate,
                          String status, boolean mine) {}
