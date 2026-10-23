package com.futbolmanager.group;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tenancy gate: a non-member sees 404 (never 403); a member lacking the role sees 403. */
@Component
public class GroupAccess {

    private final Membership.Repo memberships;

    public GroupAccess(Membership.Repo memberships) {
        this.memberships = memberships;
    }

    public Membership requireMember(UUID groupId, AuthUser user) {
        return memberships
                .findByGroupIdAndUserId(groupId, user.id())
                .orElseThrow(() -> ApiException.notFound("Grupo no encontrado."));
    }

    public Membership requireAdmin(UUID groupId, AuthUser user) {
        Membership membership = requireMember(groupId, user);
        if (membership.getRole() != Membership.Role.ADMIN) {
            throw ApiException.forbidden("Solo un administrador del grupo puede hacer esto.");
        }
        return membership;
    }
}
