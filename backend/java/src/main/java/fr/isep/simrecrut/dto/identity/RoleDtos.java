package fr.isep.simrecrut.dto.identity;

public final class RoleDtos {

    private RoleDtos() {
    }

    public record RoleSummary(String code, String name, String description) {
    }
}
