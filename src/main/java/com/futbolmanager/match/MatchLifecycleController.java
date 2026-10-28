package com.futbolmanager.match;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import com.futbolmanager.elo.EloReplayService;
import com.futbolmanager.group.GroupAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Cierre, cambio de MVP y anulación de partidos: toda transición que toca el libro ELO termina en un replay. */
@RestController
@RequestMapping("/api/groups/{groupId}/matches/{matchId}")
@Transactional
public class MatchLifecycleController {

    private static final String MATCH_NOT_FOUND = "Partido no encontrado.";

    private final Match.Repo matches;
    private final LineupEntry.Repo lineup;
    private final GroupAccess access;
    private final EloReplayService replay;
    private final JdbcClient jdbc;
    private final Clock clock;

    public MatchLifecycleController(
            Match.Repo matches,
            LineupEntry.Repo lineup,
            GroupAccess access,
            EloReplayService replay,
            JdbcClient jdbc,
            Clock clock) {
        this.matches = matches;
        this.lineup = lineup;
        this.access = access;
        this.replay = replay;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public record CloseRequest(UUID mvpPlayerId) {}

    public record MvpRequest(@NotNull UUID mvpPlayerId) {}

    public record EloChange(UUID playerId, String name, int eloBefore, int eloAfter, int delta) {}

    public record CloseResponse(int scoreA, int scoreB, UUID mvpPlayerId, List<EloChange> eloChanges) {}

    public record AnnulResponse(UUID matchId, String status) {}

    @PostMapping("/close")
    public CloseResponse close(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @RequestBody(required = false) CloseRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = load(groupId, matchId);
        if (match.getStatus() != Match.Status.LIVE) {
            throw ApiException.conflict("Solo se puede cerrar un partido en juego.");
        }
        UUID mvp = body == null ? null : body.mvpPlayerId();
        requireInLineup(match, mvp);
        match.setStatus(Match.Status.CLOSED);
        matches.saveAndFlush(match);
        jdbc.sql("update matches set mvp_player_id = cast(:mvp as uuid), closed_at = :closedAt where id = :matchId")
                .param("mvp", mvp)
                .param("closedAt", Timestamp.from(clock.instant()))
                .param("matchId", matchId)
                .update();
        replay.replayFrom(groupId, matchId);
        return response(groupId, matchId);
    }

    @PutMapping("/mvp")
    public CloseResponse changeMvp(
            @PathVariable UUID groupId,
            @PathVariable UUID matchId,
            @Valid @RequestBody MvpRequest body,
            @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = load(groupId, matchId);
        if (match.getStatus() != Match.Status.CLOSED) {
            throw ApiException.conflict("Solo se puede cambiar el MVP de un partido cerrado.");
        }
        requireInLineup(match, body.mvpPlayerId());
        jdbc.sql("update matches set mvp_player_id = :mvp where id = :matchId")
                .param("mvp", body.mvpPlayerId())
                .param("matchId", matchId)
                .update();
        replay.replayFrom(groupId, matchId);
        return response(groupId, matchId);
    }

    @PostMapping("/annul")
    public AnnulResponse annul(
            @PathVariable UUID groupId, @PathVariable UUID matchId, @AuthenticationPrincipal AuthUser user) {
        access.requireAdmin(groupId, user);
        Match match = load(groupId, matchId);
        boolean wasClosed = match.getStatus() == Match.Status.CLOSED;
        if (!wasClosed && match.getStatus() != Match.Status.SCHEDULED) {
            throw ApiException.conflict("Solo se puede anular un partido programado o cerrado.");
        }
        match.setStatus(Match.Status.ANNULLED);
        matches.saveAndFlush(match);
        jdbc.sql("update matches set annulled_at = :annulledAt where id = :matchId")
                .param("annulledAt", Timestamp.from(clock.instant()))
                .param("matchId", matchId)
                .update();
        if (wasClosed) {
            replay.replayFrom(groupId, matchId);
        }
        return new AnnulResponse(matchId, Match.Status.ANNULLED.name());
    }

    private Match load(UUID groupId, UUID matchId) {
        return matches.findByIdAndGroupId(matchId, groupId).orElseThrow(() -> ApiException.notFound(MATCH_NOT_FOUND));
    }

    private void requireInLineup(Match match, UUID playerId) {
        if (playerId != null
                && lineup.findAllByMatchId(match.getId()).stream().noneMatch(e -> e.getPlayerId().equals(playerId))) {
            throw ApiException.unprocessable("El MVP tiene que ser un jugador del partido.");
        }
    }

    private CloseResponse response(UUID groupId, UUID matchId) {
        List<EloChange> changes = replay.changesOf(groupId, matchId).stream()
                .map(c -> new EloChange(c.playerId(), c.name(), c.eloBefore(), c.eloAfter(), c.delta()))
                .toList();
        return jdbc.sql("select score_a, score_b, mvp_player_id from matches where id = :matchId")
                .param("matchId", matchId)
                .query((rs, row) -> new CloseResponse(
                        rs.getInt("score_a"), rs.getInt("score_b"), rs.getObject("mvp_player_id", UUID.class), changes))
                .single();
    }
}
