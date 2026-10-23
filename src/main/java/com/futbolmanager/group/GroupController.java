package com.futbolmanager.group;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
@Transactional
public class GroupController {

    private static final int INVITE_CODE_ATTEMPTS = 20;
    private static final String LAST_ADMIN = "El grupo tiene que tener al menos un administrador.";

    private final FriendGroup.Repo groups;
    private final Membership.Repo memberships;
    private final GroupAccess access;
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public GroupController(
            FriendGroup.Repo groups,
            Membership.Repo memberships,
            GroupAccess access,
            JdbcClient jdbc,
            ApplicationEventPublisher events) {
        this.groups = groups;
        this.memberships = memberships;
        this.access = access;
        this.jdbc = jdbc;
        this.events = events;
    }

    /** Published when a user becomes a member (group creation included). */
    public record MemberJoined(UUID groupId, UUID userId, String displayName) {}

    /** Published when a membership is removed (kicked or left). */
    public record MemberRemoved(UUID groupId, UUID userId) {}

    public record CreateGroupRequest(@NotBlank @Size(max = 60) String name) {}

    public record JoinRequest(@NotBlank @Size(max = 32) String inviteCode) {}

    public record UpdateGroupRequest(@Size(min = 1, max = 60) String name, Boolean membersCanScore) {}

    public record RoleRequest(@NotBlank @Pattern(regexp = "ADMIN|MEMBER") String role) {}

    public record GroupSummary(UUID id, String name, String myRole) {}

    public record GroupDetail(
            UUID id, String name, String inviteCode, boolean membersCanScore, String myRole, UUID myUserId) {}

    public record JoinResponse(UUID groupId) {}

    public record InviteCodeResponse(String inviteCode) {}

    public record MemberResponse(UUID userId, String username, String displayName, String role) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<GroupSummary> list(@AuthenticationPrincipal AuthUser user) {
        List<Membership> mine = memberships.findAllByUserId(user.id());
        return groups.findAllById(mine.stream().map(Membership::getGroupId).toList()).stream()
                .sorted(Comparator.comparing(FriendGroup::getName, String.CASE_INSENSITIVE_ORDER))
                .map(g -> new GroupSummary(
                        g.getId(),
                        g.getName(),
                        mine.stream()
                                .filter(m -> m.getGroupId().equals(g.getId()))
                                .findFirst()
                                .orElseThrow()
                                .getRole()
                                .name()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<GroupDetail> create(
            @Valid @RequestBody CreateGroupRequest body, @AuthenticationPrincipal AuthUser user) {
        String name = requireName(body.name());
        FriendGroup group = groups.saveAndFlush(new FriendGroup(name, freshInviteCode(), user.id()));
        Membership admin = memberships.saveAndFlush(new Membership(group.getId(), user.id(), Membership.Role.ADMIN));
        events.publishEvent(new MemberJoined(group.getId(), user.id(), user.displayName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(group, admin));
    }

    @PostMapping("/join")
    public JoinResponse join(@Valid @RequestBody JoinRequest body, @AuthenticationPrincipal AuthUser user) {
        String code = body.inviteCode().trim().toUpperCase(Locale.ROOT);
        FriendGroup group = groups.findByInviteCode(code)
                .orElseThrow(() -> ApiException.notFound("Código de invitación inválido."));
        if (memberships.findByGroupIdAndUserId(group.getId(), user.id()).isEmpty()) {
            memberships.saveAndFlush(new Membership(group.getId(), user.id(), Membership.Role.MEMBER));
            events.publishEvent(new MemberJoined(group.getId(), user.id(), user.displayName()));
        }
        return new JoinResponse(group.getId());
    }

    @GetMapping("/{groupId}")
    @Transactional(readOnly = true)
    public GroupDetail get(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        return detail(loadGroup(groupId), me);
    }

    @PatchMapping("/{groupId}")
    public GroupDetail update(
            @PathVariable UUID groupId,
            @Valid @RequestBody UpdateGroupRequest body,
            @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireAdmin(groupId, user);
        FriendGroup group = loadGroup(groupId);
        if (body.name() != null) {
            group.setName(requireName(body.name()));
        }
        if (body.membersCanScore() != null) {
            group.setMembersCanScore(body.membersCanScore());
        }
        return detail(groups.saveAndFlush(group), me);
    }

    @PostMapping("/{groupId}/invite-code")
    public InviteCodeResponse regenerateInviteCode(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        FriendGroup group = loadGroup(groupId);
        group.setInviteCode(freshInviteCode());
        return new InviteCodeResponse(groups.saveAndFlush(group).getInviteCode());
    }

    @GetMapping("/{groupId}/members")
    @Transactional(readOnly = true)
    public List<MemberResponse> members(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        return jdbc.sql("""
                        select u.id, u.username, u.display_name, m.role
                        from memberships m join users u on u.id = m.user_id
                        where m.group_id = :groupId
                        order by lower(u.display_name), u.id
                        """)
                .param("groupId", groupId)
                .query((rs, n) -> new MemberResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role")))
                .list();
    }

    @PatchMapping("/{groupId}/members/{userId}")
    public MemberResponse changeRole(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody RoleRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Membership target = loadMembership(groupId, userId);
        Membership.Role role = Membership.Role.valueOf(body.role());
        if (target.getRole() == Membership.Role.ADMIN
                && role != Membership.Role.ADMIN
                && memberships.countByGroupIdAndRole(groupId, Membership.Role.ADMIN) <= 1) {
            throw ApiException.conflict(LAST_ADMIN);
        }
        target.setRole(role);
        memberships.saveAndFlush(target);
        return memberResponse(groupId, userId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId, @PathVariable UUID userId, @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        boolean leaving = userId.equals(user.id());
        if (!leaving && me.getRole() != Membership.Role.ADMIN) {
            throw ApiException.forbidden("Solo un administrador del grupo puede hacer esto.");
        }
        Membership target = loadMembership(groupId, userId);
        if (target.getRole() == Membership.Role.ADMIN
                && memberships.countByGroupIdAndRole(groupId, Membership.Role.ADMIN) <= 1) {
            throw ApiException.conflict(LAST_ADMIN);
        }
        memberships.delete(target);
        memberships.flush();
        events.publishEvent(new MemberRemoved(groupId, userId));
        return ResponseEntity.noContent().build();
    }

    private FriendGroup loadGroup(UUID groupId) {
        return groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Grupo no encontrado."));
    }

    private Membership loadMembership(UUID groupId, UUID userId) {
        return memberships
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> ApiException.notFound("Miembro no encontrado."));
    }

    private MemberResponse memberResponse(UUID groupId, UUID userId) {
        return jdbc.sql("""
                        select u.id, u.username, u.display_name, m.role
                        from memberships m join users u on u.id = m.user_id
                        where m.group_id = :groupId and m.user_id = :userId
                        """)
                .param("groupId", groupId)
                .param("userId", userId)
                .query((rs, n) -> new MemberResponse(
                        rs.getObject("id", UUID.class),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getString("role")))
                .single();
    }

    private String freshInviteCode() {
        for (int i = 0; i < INVITE_CODE_ATTEMPTS; i++) {
            String code = FriendGroup.newInviteCode(random);
            if (!groups.existsByInviteCode(code)) {
                return code;
            }
        }
        throw ApiException.conflict("No pudimos generar un código de invitación. Probá de nuevo.");
    }

    private static String requireName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("El nombre del grupo no puede estar vacío.");
        }
        return name;
    }

    private static GroupDetail detail(FriendGroup group, Membership me) {
        return new GroupDetail(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.isMembersCanScore(),
                me.getRole().name(),
                me.getUserId());
    }
}
