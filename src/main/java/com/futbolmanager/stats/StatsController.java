package com.futbolmanager.stats;

import com.futbolmanager.auth.AuthUser;
import com.futbolmanager.common.ApiException;
import com.futbolmanager.group.GroupAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ranking, estadísticas por jugador e historial ELO: solo lectura sobre partidos {@code CLOSED}. */
@RestController
@RequestMapping("/api/groups/{groupId}")
@Transactional(readOnly = true)
public class StatsController {

    private static final int PROVISIONAL_BELOW = 10;

    public record RankingEntry(
            UUID playerId, String name, String nickname, int currentElo, int played, boolean provisional) {}

    public record Streak(String type, int length) {}

    public record PlayerStats(
            UUID playerId,
            String name,
            int played,
            int won,
            int drawn,
            int lost,
            int goals,
            int assists,
            int mvps,
            Streak streak,
            int currentElo,
            boolean provisional) {}

    public record EloPoint(UUID matchId, Instant playedAt, int eloAfter) {}

    private record Header(String name, int currentElo) {}

    private final GroupAccess access;
    private final JdbcClient jdbc;

    public StatsController(GroupAccess access, JdbcClient jdbc) {
        this.access = access;
        this.jdbc = jdbc;
    }

    @GetMapping("/ranking")
    public List<RankingEntry> ranking(@PathVariable UUID groupId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        return jdbc.sql("""
                        select p.id, p.name, p.nickname, p.current_elo,
                               (select count(*) from match_lineup_entries l
                                join matches m on m.id = l.match_id
                                where l.player_id = p.id and m.group_id = :groupId and m.status = 'CLOSED') as played
                        from players p
                        where p.group_id = :groupId and p.deleted_at is null
                        order by p.current_elo desc, p.name asc, p.id asc
                        """)
                .param("groupId", groupId)
                .query((rs, row) -> new RankingEntry(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("nickname"),
                        rs.getInt("current_elo"),
                        rs.getInt("played"),
                        rs.getInt("played") < PROVISIONAL_BELOW))
                .list();
    }

    @GetMapping("/players/{playerId}/stats")
    public PlayerStats stats(
            @PathVariable UUID groupId, @PathVariable UUID playerId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        Header header = header(groupId, playerId);

        // "W"/"D"/"L" de cada partido cerrado del jugador, del más reciente al más viejo.
        List<String> results = jdbc.sql("""
                        select case
                                 when m.score_a = m.score_b then 'D'
                                 when (l.team = 'A') = (m.score_a > m.score_b) then 'W'
                                 else 'L'
                               end as result
                        from match_lineup_entries l
                        join matches m on m.id = l.match_id
                        where l.player_id = :playerId and m.group_id = :groupId and m.status = 'CLOSED'
                        order by m.played_at desc, m.id desc
                        """)
                .param("playerId", playerId)
                .param("groupId", groupId)
                .query(String.class)
                .list();

        int goals = countGoals(groupId, playerId, "e.player_id");
        int assists = countGoals(groupId, playerId, "e.assist_player_id");
        int mvps = jdbc.sql("""
                        select count(*) from matches
                        where group_id = :groupId and status = 'CLOSED' and mvp_player_id = :playerId
                        """)
                .param("groupId", groupId)
                .param("playerId", playerId)
                .query(Integer.class)
                .single();

        return new PlayerStats(
                playerId,
                header.name(),
                results.size(),
                (int) results.stream().filter("W"::equals).count(),
                (int) results.stream().filter("D"::equals).count(),
                (int) results.stream().filter("L"::equals).count(),
                goals,
                assists,
                mvps,
                streak(results),
                header.currentElo(),
                results.size() < PROVISIONAL_BELOW);
    }

    @GetMapping("/players/{playerId}/elo-history")
    public List<EloPoint> eloHistory(
            @PathVariable UUID groupId, @PathVariable UUID playerId, @AuthenticationPrincipal AuthUser user) {
        access.requireMember(groupId, user);
        header(groupId, playerId);

        List<EloPoint> points = new ArrayList<>();
        jdbc.sql("""
                        select elo_after from elo_changes
                        where group_id = :groupId and player_id = :playerId and reason = 'INITIAL'
                        """)
                .param("groupId", groupId)
                .param("playerId", playerId)
                .query(Integer.class)
                .optional()
                .ifPresent(initial -> points.add(new EloPoint(null, null, initial)));
        points.addAll(jdbc.sql("""
                        select m.id, m.played_at, coalesce(b.elo_after, tr.elo_after) as elo_after
                        from elo_changes tr
                        join matches m on m.id = tr.match_id
                        left join elo_changes b
                          on b.match_id = tr.match_id and b.player_id = tr.player_id and b.reason = 'BONUS'
                        where tr.group_id = :groupId and tr.player_id = :playerId
                          and tr.reason = 'TEAM_RESULT' and m.status = 'CLOSED'
                        order by m.played_at, m.id
                        """)
                .param("groupId", groupId)
                .param("playerId", playerId)
                .query((rs, row) -> new EloPoint(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("played_at").toInstant(),
                        rs.getInt("elo_after")))
                .list());
        return points;
    }

    private Header header(UUID groupId, UUID playerId) {
        return jdbc.sql("select name, current_elo from players where id = :playerId and group_id = :groupId")
                .param("playerId", playerId)
                .param("groupId", groupId)
                .query((rs, row) -> new Header(rs.getString("name"), rs.getInt("current_elo")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Jugador no encontrado."));
    }

    /** Goles no anulados de partidos cerrados donde el jugador está en el lineup; {@code column} elige autor o asistente. */
    private int countGoals(UUID groupId, UUID playerId, String column) {
        return jdbc.sql("""
                        select count(*)
                        from match_events e
                        join matches m on m.id = e.match_id
                        where m.group_id = :groupId and m.status = 'CLOSED' and e.voided_at is null
                          and e.type = 'GOAL' and %s = :playerId
                          and exists (select 1 from match_lineup_entries l
                                      where l.match_id = m.id and l.player_id = :playerId)
                        """.formatted(column))
                .param("groupId", groupId)
                .param("playerId", playerId)
                .query(Integer.class)
                .single();
    }

    private static Streak streak(List<String> latestFirst) {
        if (latestFirst.isEmpty()) {
            return null;
        }
        String type = latestFirst.get(0);
        int length = 0;
        while (length < latestFirst.size() && latestFirst.get(length).equals(type)) {
            length++;
        }
        return new Streak(type, length);
    }
}
