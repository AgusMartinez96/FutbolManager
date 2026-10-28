package com.futbolmanager.elo;

import com.futbolmanager.common.ApiException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Único escritor de las filas {@code TEAM_RESULT}/{@code BONUS} del libro {@code elo_changes}. Cerrar, editar o
 * anular un partido es volver a calcular desde ese partido en adelante; las filas {@code INITIAL} no se tocan.
 */
@Service
@Transactional
public class EloReplayService {

    /** Cambio neto de un jugador en un partido: resultado de equipo más bonus. */
    public record PlayerChange(UUID playerId, String name, int eloBefore, int eloAfter, int delta) {}

    private record ClosedMatch(UUID id, UUID mvpPlayerId) {}

    private record Participant(UUID playerId, String team) {}

    private static final String PIVOT = "(select p.played_at, p.id from matches p where p.id = :matchId)";

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final EloCalculator calculator = new EloCalculator();

    public EloReplayService(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void replayFrom(UUID groupId, UUID matchId) {
        jdbc.sql("select id from groups where id = :groupId for update")
                .param("groupId", groupId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> ApiException.notFound("Grupo no encontrado."));
        long found = jdbc.sql("select count(*) from matches where id = :matchId and group_id = :groupId")
                .param("matchId", matchId)
                .param("groupId", groupId)
                .query(Long.class)
                .single();
        if (found == 0) {
            throw ApiException.notFound("Partido no encontrado.");
        }

        deleteLedgerFrom(groupId, matchId);
        Map<UUID, Integer> elo = rebuildCurrentElo(groupId);
        Map<UUID, Integer> counted = countedMatchesBefore(groupId, matchId);
        for (ClosedMatch match : closedMatchesFrom(groupId, matchId)) {
            replayMatch(groupId, match, elo, counted);
        }
    }

    /** Cambio neto por jugador de un partido ya calculado, ordenado por nombre. */
    @Transactional(readOnly = true)
    public List<PlayerChange> changesOf(UUID groupId, UUID matchId) {
        return jdbc.sql("""
                        select p.id, p.name, tr.elo_before,
                               coalesce(b.elo_after, tr.elo_after) as elo_after,
                               tr.delta + coalesce(b.delta, 0) as delta
                        from elo_changes tr
                        join players p on p.id = tr.player_id
                        left join elo_changes b
                          on b.match_id = tr.match_id and b.player_id = tr.player_id and b.reason = 'BONUS'
                        where tr.group_id = :groupId and tr.match_id = :matchId and tr.reason = 'TEAM_RESULT'
                        order by p.name, p.id
                        """)
                .param("groupId", groupId)
                .param("matchId", matchId)
                .query((rs, row) -> new PlayerChange(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getInt("elo_before"),
                        rs.getInt("elo_after"),
                        rs.getInt("delta")))
                .list();
    }

    private void deleteLedgerFrom(UUID groupId, UUID matchId) {
        jdbc.sql("""
                        delete from elo_changes
                        where group_id = :groupId and reason <> 'INITIAL' and match_id in (
                          select m.id from matches m
                          where m.group_id = :groupId and (m.played_at, m.id) >= %s)
                        """.formatted(PIVOT))
                .param("groupId", groupId)
                .param("matchId", matchId)
                .update();
    }

    /** {@code current_elo} = INITIAL.elo_after + Σ delta del resto de las filas, para todo jugador del grupo. */
    private Map<UUID, Integer> rebuildCurrentElo(UUID groupId) {
        jdbc.sql("""
                        update players p set current_elo =
                          coalesce((select i.elo_after from elo_changes i
                                    where i.player_id = p.id and i.reason = 'INITIAL'), p.current_elo)
                          + coalesce((select sum(e.delta) from elo_changes e
                                      where e.player_id = p.id and e.reason <> 'INITIAL'), 0)
                        where p.group_id = :groupId
                        """)
                .param("groupId", groupId)
                .update();
        Map<UUID, Integer> elo = new HashMap<>();
        jdbc.sql("select id, current_elo from players where group_id = :groupId")
                .param("groupId", groupId)
                .query((rs, row) -> elo.put(rs.getObject("id", UUID.class), rs.getInt("current_elo")))
                .list();
        return elo;
    }

    private Map<UUID, Integer> countedMatchesBefore(UUID groupId, UUID matchId) {
        Map<UUID, Integer> counted = new HashMap<>();
        jdbc.sql("""
                        select l.player_id, count(*) as played
                        from match_lineup_entries l
                        join matches m on m.id = l.match_id
                        where m.group_id = :groupId and m.status = 'CLOSED' and (m.played_at, m.id) < %s
                        group by l.player_id
                        """.formatted(PIVOT))
                .param("groupId", groupId)
                .param("matchId", matchId)
                .query((rs, row) -> counted.put(rs.getObject("player_id", UUID.class), rs.getInt("played")))
                .list();
        return counted;
    }

    private List<ClosedMatch> closedMatchesFrom(UUID groupId, UUID matchId) {
        return jdbc.sql("""
                        select m.id, m.mvp_player_id from matches m
                        where m.group_id = :groupId and m.status = 'CLOSED' and (m.played_at, m.id) >= %s
                        order by m.played_at, m.id
                        """.formatted(PIVOT))
                .param("groupId", groupId)
                .param("matchId", matchId)
                .query((rs, row) ->
                        new ClosedMatch(rs.getObject("id", UUID.class), rs.getObject("mvp_player_id", UUID.class)))
                .list();
    }

    private void replayMatch(UUID groupId, ClosedMatch match, Map<UUID, Integer> elo, Map<UUID, Integer> counted) {
        List<Participant> lineup = jdbc.sql("select player_id, team from match_lineup_entries where match_id = :matchId")
                .param("matchId", match.id())
                .query((rs, row) -> new Participant(rs.getObject("player_id", UUID.class), rs.getString("team")))
                .list();

        Map<UUID, Integer> goals = new HashMap<>();
        Map<UUID, Integer> assists = new HashMap<>();
        Map<UUID, Integer> saves = new HashMap<>();
        Map<UUID, Integer> tackles = new HashMap<>();
        Map<UUID, Integer> plays = new HashMap<>();
        int[] score = new int[2];
        jdbc.sql("""
                        select type, team, player_id, assist_player_id from match_events
                        where match_id = :matchId and voided_at is null
                        """)
                .param("matchId", match.id())
                .query((rs, row) -> {
                    UUID player = rs.getObject("player_id", UUID.class);
                    UUID assist = rs.getObject("assist_player_id", UUID.class);
                    switch (rs.getString("type")) {
                        case "GOAL" -> {
                            score["A".equals(rs.getString("team")) ? 0 : 1]++;
                            add(goals, player);
                            add(assists, assist);
                        }
                        case "SAVE" -> add(saves, player);
                        case "TACKLE" -> add(tackles, player);
                        case "PLAY" -> add(plays, player);
                        default -> throw new IllegalStateException("Tipo de evento desconocido.");
                    }
                    return null;
                })
                .list();

        List<EloCalculator.PlayerInput> teamA = new ArrayList<>();
        List<EloCalculator.PlayerInput> teamB = new ArrayList<>();
        for (Participant p : lineup) {
            UUID id = p.playerId();
            EloCalculator.PlayerInput input = new EloCalculator.PlayerInput(
                    id,
                    elo.get(id),
                    counted.getOrDefault(id, 0),
                    goals.getOrDefault(id, 0),
                    assists.getOrDefault(id, 0),
                    saves.getOrDefault(id, 0),
                    tackles.getOrDefault(id, 0),
                    plays.getOrDefault(id, 0));
            ("A".equals(p.team()) ? teamA : teamB).add(input);
        }

        List<EloCalculator.PlayerResult> results =
                calculator.calculate(new EloCalculator.MatchInput(teamA, teamB, score[0], score[1], match.mvpPlayerId()));
        for (EloCalculator.PlayerResult result : results) {
            int before = elo.get(result.playerId());
            int afterTeam = before + result.teamDelta();
            insert(groupId, match.id(), result.playerId(), "TEAM_RESULT", before, result.teamDelta(), null);
            int after = afterTeam;
            if (result.bonus() > 0) {
                after = afterTeam + result.bonus();
                insert(groupId, match.id(), result.playerId(), "BONUS", afterTeam, result.bonus(), details(result));
            }
            elo.put(result.playerId(), after);
            counted.merge(result.playerId(), 1, Integer::sum);
            jdbc.sql("update players set current_elo = :elo where id = :playerId")
                    .param("elo", after)
                    .param("playerId", result.playerId())
                    .update();
        }
        jdbc.sql("update matches set score_a = :scoreA, score_b = :scoreB where id = :matchId")
                .param("scoreA", score[0])
                .param("scoreB", score[1])
                .param("matchId", match.id())
                .update();
    }

    private void insert(
            UUID groupId, UUID matchId, UUID playerId, String reason, int before, int delta, String details) {
        jdbc.sql("""
                        insert into elo_changes (group_id, player_id, match_id, reason, elo_before, elo_after, delta, details)
                        values (:groupId, :playerId, :matchId, :reason, :before, :after, :delta, cast(:details as jsonb))
                        """)
                .param("groupId", groupId)
                .param("playerId", playerId)
                .param("matchId", matchId)
                .param("reason", reason)
                .param("before", before)
                .param("after", before + delta)
                .param("delta", delta)
                .param("details", details)
                .update();
    }

    private String details(EloCalculator.PlayerResult result) {
        EloCalculator.BonusBreakdown b = result.breakdown();
        Map<String, Integer> details = new LinkedHashMap<>();
        details.put("goals", b.goals());
        details.put("assists", b.assists());
        details.put("mvp", b.mvp());
        details.put("cleanSheet", b.cleanSheet());
        details.put("saves", b.saves());
        details.put("tackles", b.tackles());
        details.put("plays", b.plays());
        details.put("uncapped", b.uncapped());
        details.put("total", b.total());
        return json.writeValueAsString(details);
    }

    private static void add(Map<UUID, Integer> counts, UUID playerId) {
        if (playerId != null) {
            counts.merge(playerId, 1, Integer::sum);
        }
    }
}
