package fr.isep.simrecrut.security.currentuser;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/** Identity created only after token, session and current account validation. */
public record AccountPrincipal(
        long userId, String publicId, String sessionId, long securityVersion, Instant expiresAt)
        implements Principal {

    public AccountPrincipal {
        if (userId < 1 || publicId == null || !publicId.matches("[0-9A-HJKMNP-TV-Z]{26}")
                || sessionId == null || !UUID.fromString(sessionId).toString().equals(sessionId)
                || securityVersion < 0 || expiresAt == null) {
            throw new IllegalArgumentException("Invalid authenticated identity.");
        }
    }

    @Override
    public String getName() {
        return publicId;
    }
}
