package com.futbolmanager.match;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import com.futbolmanager.group.GroupAccess;
import com.futbolmanager.group.Membership;
import com.futbolmanager.match.balance.TeamBalancer;
import com.futbolmanager.player.Player;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/matches")
@Transactional
public class MatchController {

    private static final String MATCH_NOT_FOUND = "Partido no encontrado.";
    private static final String ONLY_SCHEDULED = "Solo se puede hacer esto en un partido programado.";
    private static final String FORMATS = "5|7|8|11";

    private final Match.Repo matches;
    private final MatchCallUp.Repo callUps;
    private final LineupEntry.Repo lineup;
    private final Player.Repo players;
    private final GroupAccess access;
    private final TeamBalancer balancer = new TeamBalancer();

    public MatchController(
            Match.Repo matches,
            MatchCallUp.Repo callUps,
            LineupEntry.Repo lineup,
            Player.Repo players,
            GroupAccess access) {
        this.matches = matches;
        this.callUps = callUps;
        this.lineup = lineup;
        this.players = players;
        this.access = access;
    }

    public record CreateMatchRequest(
            @NotNull Instant playedAt,
            @NotBlank @Size(max = 120) String location,
            @NotNull @Pattern(regexp = FORMATS) String format) {}

    public record UpdateMatchRequest(
            Instant playedAt, @Size(min = 1, max = 120) String location, @Pattern(regexp = FORMATS) String format) {}

    public record CallUpRequest(@NotNull @Pattern(regexp = "CONFIRMED|MAYBE|NOT_GOING") String status) {}

    public record LineupItem(
            @NotNull UUID playerId,
            @NotNull @Pattern(regexp = "A|B") String team,
            @NotNull @Pattern(regexp = "GK|DEF|MID|FWD") String position) {}

    public record LineupRequest(@NotNull @NotEmpty List<@Valid LineupItem> entries) {}

    public record CallUpResponse(UUID playerId, String name, int currentElo, String status) {}

    public record LineupResponse(UUID playerId, String name, int currentElo, String team, String position) {}

    /** {@code position} es el puesto preferido del jugador (informativo); el balanceo no lo lee. */
    public record BalancedPlayer(UUID playerId, String name, int currentElo, BigDecimal level, String position) {}

    public record BalanceOption(
            List<BalancedPlayer> teamA,
            List<BalancedPlayer> teamB,
            double totalLevelA,
            double totalLevelB,
            double levelDifference,
            double avgEloA,
            double avgEloB,
            double eloDifference) {}

    public record BalanceResponse(List<BalanceOption> options) {}

    public record MatchSummary(
            UUID id,
            Instant playedAt,
            String location,
            int format,
            String status,
            Integer scoreA,
            Integer scoreB,
            UUID mvpPlayerId) {}

    public record MatchDetail(
            UUID id,
            Instant playedAt,
            String location,
            int format,
            String status,
            Integer scoreA,
            Integer scoreB,
            UUID mvpPlayerId,
            List<CallUpResponse> callUps,
            List<LineupResponse> lineup) {}

    @GetMapping
    @Transactional(readOnly = true)
    public List<MatchSummary> list(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        return matches.findTop200ByGroupIdOrderByPlayedAtDescIdDesc(groupId).stream()
                .map(MatchController::summary)
                .toList();
    }

    @PostMapping
    public ResponseEntity<MatchDetail> create(
            @PathVariable UUID groupId,
            @Valid @RequestBody CreateMatchRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = matches.saveAndFlush(
                new Match(groupId, body.playedAt(), body.location().trim(), Integer.parseInt(body.format())));
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(match));
    }

    @GetMapping("/{matchId}")
    @Transactional(readOnly = true)
    public MatchDetail get(
            @PathVariable UUID groupId, @PathVariable UUID matchId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        return detail(load(groupId, matchId));
    }

    @PatchMapping("/{matchId}")
    public MatchDetail update(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody UpdateMatchRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = loadScheduled(groupId, matchId);
        if (body.playedAt() != null) {
            match.setPlayedAt(body.playedAt());
        }
        if (body.location() != null) {
            match.setLocation(body.location().trim());
        }
        if (body.format() != null) {
            match.setFormat(Integer.parseInt(body.format()));
        }
        return detail(matches.saveAndFlush(match));
    }

    @PutMapping("/{matchId}/call-ups/{playerId}")
    public CallUpResponse setCallUp(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @PathVariable UUID playerId,
            @Valid @RequestBody CallUpRequest body,
            @AuthenticationPrincipal AuthUser user) {
        Membership me = access.requireMember(groupId, user);
        Match match = loadScheduled(groupId, matchId);
        Player player = activePlayers(groupId, List.of(playerId)).get(playerId);
        if (player == null) {
            throw ApiException.unprocessable("El jugador no pertenece al grupo.");
        }
        if (me.getRole() != Membership.Role.ADMIN && !user.id().equals(player.getUserId())) {
            throw ApiException.forbidden("Solo podés cambiar tu propia convocatoria.");
        }
        MatchCallUp.Status status = MatchCallUp.Status.valueOf(body.status());
        MatchCallUp callUp = callUps.findByMatchIdAndPlayerId(match.getId(), playerId)
                .orElseGet(() -> new MatchCallUp(match.getId(), playerId, status));
        callUp.setStatus(status);
        callUps.saveAndFlush(callUp);
        return new CallUpResponse(playerId, player.getName(), player.getCurrentElo(), status.name());
    }

    @PostMapping("/{matchId}/balance")
    @Transactional(readOnly = true)
    public BalanceResponse balance(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = loadScheduled(groupId, matchId);
        List<UUID> confirmedIds = callUps.findAllByMatchId(match.getId()).stream()
                .filter(callUp -> callUp.getStatus() == MatchCallUp.Status.CONFIRMED)
                .map(MatchCallUp::getPlayerId)
                .toList();
        Map<UUID, Player> byId = activePlayers(groupId, confirmedIds);
        if (byId.size() < 2) {
            throw ApiException.badRequest("Necesitás al menos 2 confirmados para armar equipos.");
        }
        if (byId.size() > 22) {
            throw ApiException.badRequest("El armado automático admite hasta 22 jugadores.");
        }
        List<TeamBalancer.Candidate> candidates = byId.values().stream()
                .map(p -> new TeamBalancer.Candidate(p.getId(), p.getLevelTenths(), p.getCurrentElo()))
                .toList();
        return new BalanceResponse(balancer.balance(candidates).stream()
                .map(result -> new BalanceOption(
                        balanced(result.teamA(), byId),
                        balanced(result.teamB(), byId),
                        result.totalLevelA(),
                        result.totalLevelB(),
                        result.levelDifference(),
                        result.avgEloA(),
                        result.avgEloB(),
                        result.eloDifference()))
                .toList());
    }

    @PutMapping("/{matchId}/lineup")
    public List<LineupResponse> saveLineup(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody LineupRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = loadScheduled(groupId, matchId);
        List<UUID> ids = body.entries().stream().map(LineupItem::playerId).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw ApiException.unprocessable("Un jugador no puede aparecer dos veces en el lineup.");
        }
        Map<UUID, Player> byId = activePlayers(groupId, ids);
        if (byId.size() != ids.size()) {
            throw ApiException.unprocessable("El lineup incluye jugadores que no pertenecen al grupo.");
        }
        lineup.deleteAllByMatchId(match.getId());
        lineup.flush();
        lineup.saveAllAndFlush(body.entries().stream()
                .map(item -> new LineupEntry(
                        match.getId(),
                        item.playerId(),
                        LineupEntry.Team.valueOf(item.team()),
                        Player.Position.valueOf(item.position())))
                .toList());
        return lineupOf(match.getId());
    }

    @PostMapping("/{matchId}/start")
    public MatchDetail start(
            @PathVariable UUID groupId, @PathVariable UUID matchId, @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = loadScheduled(groupId, matchId);
        Set<LineupEntry.Team> teams = lineup.findAllByMatchId(match.getId()).stream()
                .map(LineupEntry::getTeam)
                .collect(Collectors.toSet());
        if (teams.size() < LineupEntry.Team.values().length) {
            throw ApiException.conflict("Guardá los equipos antes de empezar.");
        }
        match.setStatus(Match.Status.LIVE);
        return detail(matches.saveAndFlush(match));
    }

    private Match load(UUID groupId, UUID matchId) {
        return matches.findByIdAndGroupId(matchId, groupId).orElseThrow(() -> ApiException.notFound(MATCH_NOT_FOUND));
    }

    private Match loadScheduled(UUID groupId, UUID matchId) {
        Match match = load(groupId, matchId);
        if (match.getStatus() != Match.Status.SCHEDULED) {
            throw ApiException.conflict(ONLY_SCHEDULED);
        }
        return match;
    }

    /** Non-deleted players of the group among {@code ids}; ids from other groups or deleted ones are absent. */
    private Map<UUID, Player> activePlayers(UUID groupId, List<UUID> ids) {
        return players.findAllById(ids).stream()
                .filter(p -> p.getGroupId().equals(groupId) && !p.isDeleted())
                .collect(Collectors.toMap(Player::getId, Function.identity()));
    }

    private List<BalancedPlayer> balanced(List<UUID> team, Map<UUID, Player> byId) {
        return team.stream()
                .map(id -> {
                    Player p = byId.get(id);
                    return new BalancedPlayer(
                            p.getId(),
                            p.getName(),
                            p.getCurrentElo(),
                            BigDecimal.valueOf(p.getLevelTenths(), 1),
                            p.getPreferredPosition().name());
                })
                .toList();
    }

    private List<LineupResponse> lineupOf(UUID matchId) {
        List<LineupEntry> entries = lineup.findAllByMatchId(matchId);
        Map<UUID, Player> byId = players.findAllById(
                        entries.stream().map(LineupEntry::getPlayerId).toList())
                .stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        return entries.stream()
                .map(e -> {
                    Player p = byId.get(e.getPlayerId());
                    return new LineupResponse(
                            p.getId(), p.getName(), p.getCurrentElo(), e.getTeam().name(), e.getPosition().name());
                })
                .sorted(Comparator.comparing(LineupResponse::team).thenComparing(LineupResponse::name))
                .toList();
    }

    private MatchDetail detail(Match match) {
        List<MatchCallUp> rows = callUps.findAllByMatchId(match.getId());
        Map<UUID, Player> byId = players.findAllById(
                        rows.stream().map(MatchCallUp::getPlayerId).toList())
                .stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));
        List<CallUpResponse> callUpResponses = rows.stream()
                .map(c -> {
                    Player p = byId.get(c.getPlayerId());
                    return new CallUpResponse(p.getId(), p.getName(), p.getCurrentElo(), c.getStatus().name());
                })
                .sorted(Comparator.comparing(CallUpResponse::name))
                .toList();
        return new MatchDetail(
                match.getId(),
                match.getPlayedAt(),
                match.getLocation(),
                match.getFormat(),
                match.getStatus().name(),
                match.getScoreA(),
                match.getScoreB(),
                match.getMvpPlayerId(),
                callUpResponses,
                lineupOf(match.getId()));
    }

    private static MatchSummary summary(Match match) {
        return new MatchSummary(
                match.getId(),
                match.getPlayedAt(),
                match.getLocation(),
                match.getFormat(),
                match.getStatus().name(),
                match.getScoreA(),
                match.getScoreB(),
                match.getMvpPlayerId());
    }
}
