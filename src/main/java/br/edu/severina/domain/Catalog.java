package br.edu.severina.domain;

import java.util.List;

public final class Catalog {
    public static final List<String> ROOMS = List.of("PPA", "PPB", "PPC", "M1A", "M1B", "M1C", "M2A", "M2B", "M2C");
    private Catalog() {}
    public record Equipment(long id, String name) {}
    public record View(List<String> rooms, List<Equipment> equipment) {}
}
