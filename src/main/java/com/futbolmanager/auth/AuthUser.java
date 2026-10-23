package com.futbolmanager.auth;

import java.io.Serializable;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Spring Session stores {@code Authentication.getName()} in {@code SPRING_SESSION.PRINCIPAL_NAME}
 * (varchar(100)); without {@link #getName()} it falls back to this record's long {@code toString()}.
 */
public record AuthUser(UUID id, String username, String displayName) implements AuthenticatedPrincipal, Serializable {

    @Override
    public String getName() {
        return id.toString();
    }
}
