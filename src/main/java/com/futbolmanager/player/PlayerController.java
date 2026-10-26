package com.futbolmanager.player;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import com.futbolmanager.group.GroupAccess;
import com.futbolmanager.group.Membership;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/groups/{groupId}/players")
@Transactional
public class PlayerController {

    private static final String PLAYER_NOT_FOUND = "Jugador no encontrado.";

    private final Player.Repo players;
    private final PlayerService service;
    private final GroupAccess access;

    public PlayerController(Player.Repo players, PlayerService service, GroupAccess access) {
        this.players = players;
        this.service = service;
        this.access = access;
    }

    public record CreatePlayerRequest(
            @NotBlank @Size(max = 60) String name,
            @Size(min = 1, max = 30) String nickname,
            @Pattern(regexp = "GK|DEF|MID|FWD") String preferredPosition,
            @Pattern(regexp = "RIGHT|LEFT|BOTH") String dominantFoot,
            @NotNull @Min(1) @Max(10) Integer level) {}

    public record UpdatePlayerRequest(
            @Size(min = 1, max = 60) String name,
            @Size(min = 1, max = 30) String nickname,
            @Pattern(regexp = "GK|DEF|MID|FWD") String preferredPosition,
            @Pattern(regexp = "RIGHT|LEFT|BOTH") String dominantFoot,
            @Min(1) @Max(10) Integer level) {}

    /** {@code level} is only filled for group admins; it is omitted from the JSON otherwise. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlayerResponse(
            UUID id,
            String name,
            String nickname,
            String preferredPosition,
            String dominantFoot,
            int currentElo,
            UUID userId,
            Integer level) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<PlayerResponse> list(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        boolean admin = access.requireMember(groupId, user).getRole() == Membership.Role.ADMIN;
        return players.findAllByGroupIdAndDeletedAtIsNullOrderByNameAsc(groupId).stream()
                .map(player -> response(player, admin))
                .toList();
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreatePlayerRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Player player = service.create(
                groupId,
                null,
                requireName(body.name()),
                body.nickname(),
                body.preferredPosition() == null ? Player.Position.MID : Player.Position.valueOf(body.preferredPosition()),
                body.dominantFoot() == null ? Player.Foot.RIGHT : Player.Foot.valueOf(body.dominantFoot()),
                body.level());
        return ResponseEntity.status(HttpStatus.CREATED).body(response(player, true));
    }

    @PatchMapping("/{playerId}")
    public PlayerResponse update(
            @PathVariable UUID groupId,
            @PathVariable UUID playerId,
            @Valid @RequestBody UpdatePlayerRequest body,
            @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        Player player = loadActive(groupId, playerId);
        boolean isOwnPlayer = user.id().equals(player.getUserId());
        boolean admin = me.getRole() == Membership.Role.ADMIN;
        if (!admin && !isOwnPlayer) {
            throw ApiException.forbidden("Solo un administrador del grupo puede hacer esto.");
        }
        if (!admin && body.level() != null) {
            throw ApiException.forbidden("Solo un administrador puede cambiar el nivel.");
        }
        if (body.level() != null) {
            player.setLevel(body.level());
        }
        if (body.name() != null) {
            player.setName(requireName(body.name()));
        }
        if (body.nickname() != null) {
            player.setNickname(body.nickname());
        }
        if (body.preferredPosition() != null) {
            player.setPreferredPosition(Player.Position.valueOf(body.preferredPosition()));
        }
        if (body.dominantFoot() != null) {
            player.setDominantFoot(Player.Foot.valueOf(body.dominantFoot()));
        }
        return response(players.saveAndFlush(player), admin);
    }

    @DeleteMapping("/{playerId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID groupId, @PathVariable UUID playerId, @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        service.softDelete(loadActive(groupId, playerId));
        return ResponseEntity.noContent().build();
    }

    private Player loadActive(UUID groupId, UUID playerId) {
        return players.findByIdAndGroupId(playerId, groupId)
                .filter(player -> !player.isDeleted())
                .orElseThrow(() -> ApiException.notFound(PLAYER_NOT_FOUND));
    }

    private static String requireName(String raw) {
        String name = raw.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("El nombre del jugador no puede estar vacío.");
        }
        return name;
    }

    private static PlayerResponse response(Player player, boolean includeLevel) {
        return new PlayerResponse(
                player.getId(),
                player.getName(),
                player.getNickname(),
                player.getPreferredPosition().name(),
                player.getDominantFoot().name(),
                player.getCurrentElo(),
                player.getUserId(),
                includeLevel ? player.getLevel() : null);
    }
}
