package com.futbolmanager.live;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.auth.User;
import com.futbolmanager.common.ApiException;
import com.futbolmanager.group.FriendGroup;
import com.futbolmanager.group.GroupAccess;
import com.futbolmanager.group.Membership;
import com.futbolmanager.match.LineupEntry;
import com.futbolmanager.match.Match;
import com.futbolmanager.player.Player;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/matches/{matchId}")
@Transactional
public class LiveController {

    private static final String MATCH_NOT_FOUND = "Partido no encontrado.";
    private static final String EVENT_NOT_FOUND = "Evento no encontrado.";
    private static final String NOT_IN_PLAY = "El partido no está en juego.";

    private final Match.Repo matches;
    private final MatchEvent.Repo events;
    private final LineupEntry.Repo lineup;
    private final Player.Repo players;
    private final User.Repo users;
    private final FriendGroup.Repo groups;
    private final GroupAccess access;

    public LiveController(
            Match.Repo matches,
            MatchEvent.Repo events,
            LineupEntry.Repo lineup,
            Player.Repo players,
            User.Repo users,
            FriendGroup.Repo groups,
            GroupAccess access) {
        this.matches = matches;
        this.events = events;
        this.lineup = lineup;
        this.players = players;
        this.users = users;
        this.groups = groups;
        this.access = access;
    }

    public record EventRequest(
            @NotNull UUID id,
            @NotNull @Pattern(regexp = "GOAL|SAVE|TACKLE") String type,
            @NotNull @Pattern(regexp = "A|B") String team,
            UUID playerId,
            UUID assistPlayerId) {}

    public record Recorder(UUID userId, String displayName) {}

    public record LiveEvent(
            UUID id,
            String type,
            String team,
            UUID playerId,
            String playerName,
            UUID assistPlayerId,
            String assistPlayerName,
            Recorder recordedBy,
            Instant createdAt,
            Instant voidedAt) {}

    public record LiveState(
            UUID matchId,
            String status,
            int scoreA,
            int scoreB,
            boolean canRecord,
            boolean isAdmin,
            UUID myUserId,
            List<LiveEvent> events) {}

    @GetMapping("/live")
    @Transactional(readOnly = true)
    public LiveState live(
            @PathVariable UUID groupId, @PathVariable UUID matchId, @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        Match match = load(groupId, matchId);
        boolean admin = me.getRole() == Membership.Role.ADMIN;
        List<MatchEvent> all = events.findAllByMatchIdOrderByCreatedAtAsc(match.getId());
        return new LiveState(
                match.getId(),
                match.getStatus().name(),
                goals(all, "A"),
                goals(all, "B"),
                canRecord(match, admin, membersCanScore(groupId)),
                admin,
                user.id(),
                views(all));
    }

    @PostMapping("/events")
    public ResponseEntity<LiveEvent> record(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody EventRequest body,
            @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        Match match = load(groupId, matchId);
        requireInPlay(match);
        boolean admin = me.getRole() == Membership.Role.ADMIN;
        if (!canRecord(match, admin, membersCanScore(groupId))) {
            throw ApiException.forbidden("No tenés permiso para registrar eventos en este partido.");
        }
        MatchEvent existing = events.findById(body.id()).orElse(null);
        if (existing != null) {
            if (!sameAs(existing, match, body)) {
                throw ApiException.conflict("Ese id de evento ya se usó con otros datos.");
            }
            return ResponseEntity.ok(view(existing));
        }
        validateParticipants(match, body);
        MatchEvent saved = events.saveAndFlush(new MatchEvent(
                body.id(),
                match.getId(),
                MatchEvent.Type.valueOf(body.type()),
                body.team(),
                body.playerId(),
                body.assistPlayerId(),
                user.id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view(saved));
    }

    @PostMapping("/events/{eventId}/void")
    public LiveEvent voidEvent(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        Match match = load(groupId, matchId);
        MatchEvent event = events.findById(eventId)
                .filter(e -> e.getMatchId().equals(match.getId()))
                .orElseThrow(() -> ApiException.notFound(EVENT_NOT_FOUND));
        requireInPlay(match);
        boolean admin = me.getRole() == Membership.Role.ADMIN;
        boolean author = event.getRecordedByUserId().equals(user.id());
        boolean allowed = admin || (author && match.getStatus() == Match.Status.LIVE);
        if (!allowed) {
            throw ApiException.forbidden("Solo quien cargó el evento o un administrador puede anularlo.");
        }
        if (!event.isVoided()) {
            event.voidBy(user.id(), Instant.now());
            events.saveAndFlush(event);
        }
        return view(event);
    }

    private Match load(UUID groupId, UUID matchId) {
        return matches.findByIdAndGroupId(matchId, groupId).orElseThrow(() -> ApiException.notFound(MATCH_NOT_FOUND));
    }

    private static void requireInPlay(Match match) {
        if (match.getStatus() == Match.Status.SCHEDULED || match.getStatus() == Match.Status.ANNULLED) {
            throw ApiException.conflict(NOT_IN_PLAY);
        }
    }

    private boolean membersCanScore(UUID groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> ApiException.notFound("Grupo no encontrado."))
                .isMembersCanScore();
    }

    private static boolean canRecord(Match match, boolean admin, boolean membersCanScore) {
        return switch (match.getStatus()) {
            case LIVE -> admin || membersCanScore;
            case CLOSED -> admin;
            case SCHEDULED, ANNULLED -> false;
        };
    }

    private static boolean sameAs(MatchEvent existing, Match match, EventRequest body) {
        return existing.getMatchId().equals(match.getId())
                && existing.getType().name().equals(body.type())
                && existing.getTeam().equals(body.team())
                && Objects.equals(existing.getPlayerId(), body.playerId())
                && Objects.equals(existing.getAssistPlayerId(), body.assistPlayerId());
    }

    private void validateParticipants(Match match, EventRequest body) {
        Map<UUID, LineupEntry> byPlayer = lineup.findAllByMatchId(match.getId()).stream()
                .collect(Collectors.toMap(LineupEntry::getPlayerId, Function.identity()));
        boolean goal = MatchEvent.Type.valueOf(body.type()) == MatchEvent.Type.GOAL;
        UUID playerId = body.playerId();
        UUID assistId = body.assistPlayerId();
        if (playerId == null && !goal) {
            throw ApiException.unprocessable("Indicá el jugador del evento.");
        }
        if (assistId != null && (!goal || playerId == null)) {
            throw ApiException.unprocessable("Solo un gol con autor puede tener asistencia.");
        }
        if (playerId != null) {
            LineupEntry entry = inTeam(byPlayer, playerId, body.team());
            if (body.type().equals("SAVE") && entry.getPosition() != Player.Position.GK) {
                throw ApiException.unprocessable("Solo el arquero del equipo puede registrar una atajada.");
            }
        }
        if (assistId != null) {
            if (assistId.equals(playerId)) {
                throw ApiException.unprocessable("La asistencia no puede ser del mismo jugador que hizo el gol.");
            }
            inTeam(byPlayer, assistId, body.team());
        }
    }

    private static LineupEntry inTeam(Map<UUID, LineupEntry> byPlayer, UUID playerId, String team) {
        LineupEntry entry = byPlayer.get(playerId);
        if (entry == null || !entry.getTeam().name().equals(team)) {
            throw ApiException.unprocessable("El jugador no está en el lineup de ese equipo.");
        }
        return entry;
    }

    private static int goals(List<MatchEvent> all, String team) {
        return (int) all.stream()
                .filter(e -> e.getType() == MatchEvent.Type.GOAL && !e.isVoided() && e.getTeam().equals(team))
                .count();
    }

    private LiveEvent view(MatchEvent event) {
        return views(List.of(event)).get(0);
    }

    private List<LiveEvent> views(List<MatchEvent> all) {
        Set<UUID> playerIds = new HashSet<>();
        Set<UUID> userIds = new HashSet<>();
        for (MatchEvent e : all) {
            if (e.getPlayerId() != null) {
                playerIds.add(e.getPlayerId());
            }
            if (e.getAssistPlayerId() != null) {
                playerIds.add(e.getAssistPlayerId());
            }
            userIds.add(e.getRecordedByUserId());
        }
        Map<UUID, String> playerNames = players.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));
        Map<UUID, String> userNames =
                users.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));
        return all.stream()
                .map(e -> new LiveEvent(
                        e.getId(),
                        e.getType().name(),
                        e.getTeam(),
                        e.getPlayerId(),
                        playerNames.get(e.getPlayerId()),
                        e.getAssistPlayerId(),
                        playerNames.get(e.getAssistPlayerId()),
                        new Recorder(e.getRecordedByUserId(), userNames.get(e.getRecordedByUserId())),
                        e.getCreatedAt(),
                        e.getVoidedAt()))
                .toList();
    }
}
