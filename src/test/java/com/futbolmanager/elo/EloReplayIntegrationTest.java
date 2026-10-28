package com.futbolmanager.elo;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.player.Player;
import com.futbolmanager.support.ApiTestClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EloReplayIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EloReplayService replay;

    @Autowired
    Player.Repo players;

    @Autowired
    PlatformTransactionManager transactionManager;

    /** Team A: the admin (GK) and "Campo A"; team B: "Arquero B" (GK) and "Campo B". */
    private record Group(
            ApiTestClient admin, String groupId, String adminPlayer, String gkB, String outfieldA, String outfieldB) {}

    private ApiTestClient newUser(String prefix) {
        ApiTestClient client = new ApiTestClient(mvc);
        assertThat(status(client.register(ApiTestClient.uniqueUsername(prefix), prefix, PASSWORD)))
                .isEqualTo(201);
        return client;
    }

    private String guest(ApiTestClient admin, String groupId, String name) {
        MvcResult created = admin.post("/api/groups/" + groupId + "/players", Map.of("name", name, "level", 5));
        assertThat(status(created)).isEqualTo(201);
        return json(created, "$.id");
    }

    private Group newGroup(ApiTestClient admin) {
        String groupId = json(admin.post("/api/groups", Map.of("name", "Los Pibes")), "$.id");
        String userId = json(admin.get("/api/auth/me"), "$.id");
        String me = jdbc.sql("select id::text from players where group_id = :g::uuid and user_id = :u::uuid")
                .param("g", groupId)
                .param("u", userId)
                .query(String.class)
                .single();
        return new Group(
                admin,
                groupId,
                me,
                guest(admin, groupId, "Arquero B"),
                guest(admin, groupId, "Campo A"),
                guest(admin, groupId, "Campo B"));
    }

    private ApiTestClient joinedMember(Group g, String prefix) {
        ApiTestClient member = newUser(prefix);
        String code = json(g.admin().get("/api/groups/" + g.groupId()), "$.inviteCode");
        assertThat(status(member.post("/api/groups/join", Map.of("inviteCode", code))))
                .isEqualTo(200);
        return member;
    }

    private Map<String, Object> entry(String playerId, String team, String position) {
        return Map.of("playerId", playerId, "team", team, "position", position);
    }

    /** Creates a match with the group's fixed lineup; started unless {@code start} is false. Returns its API path. */
    private String match(Group g, String playedAt, boolean start) {
        String matchId = json(
                g.admin()
                        .post(
                                "/api/groups/" + g.groupId() + "/matches",
                                Map.of("playedAt", playedAt, "location", "Cancha 1", "format", 7)),
                "$.id");
        String base = "/api/groups/" + g.groupId() + "/matches/" + matchId;
        assertThat(status(g.admin()
                        .put(
                                base + "/lineup",
                                Map.of(
                                        "entries",
                                        List.of(
                                                entry(g.adminPlayer(), "A", "GK"),
                                                entry(g.outfieldA(), "A", "MID"),
                                                entry(g.gkB(), "B", "GK"),
                                                entry(g.outfieldB(), "B", "FWD"))))))
                .isEqualTo(200);
        if (start) {
            assertThat(status(g.admin().post(base + "/start", null))).isEqualTo(200);
        }
        return base;
    }

    private String matchIdOf(String base) {
        return base.substring(base.lastIndexOf('/') + 1);
    }

    private Map<String, Object> event(String id, String type, String team, String playerId, String assistId) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        body.put("type", type);
        body.put("team", team);
        body.put("playerId", playerId);
        body.put("assistPlayerId", assistId);
        return body;
    }

    private String record(Group g, String base, String type, String team, String playerId, String assistId) {
        String id = UUID.randomUUID().toString();
        assertThat(status(g.admin().post(base + "/events", event(id, type, team, playerId, assistId))))
                .isEqualTo(201);
        return id;
    }

    /** Plays the 2–1 scenario: A scores twice (Campo A, assisted once by the admin), B once (Campo B). */
    private void playTwoOne(Group g, String base) {
        record(g, base, "GOAL", "A", g.outfieldA(), g.adminPlayer());
        record(g, base, "GOAL", "A", g.outfieldA(), null);
        record(g, base, "GOAL", "B", g.outfieldB(), null);
    }

    private List<String> ledger(Group g) {
        return jdbc.sql("""
                        select concat_ws('|', match_id, player_id, reason, elo_before, elo_after, delta)
                        from elo_changes where group_id = :g::uuid and reason <> 'INITIAL'
                        order by match_id, player_id, reason
                        """)
                .param("g", g.groupId())
                .query(String.class)
                .list();
    }

    private List<String> initialRows(Group g) {
        return jdbc.sql("""
                        select concat_ws('|', id, player_id, elo_before, elo_after, delta)
                        from elo_changes where group_id = :g::uuid and reason = 'INITIAL' order by player_id
                        """)
                .param("g", g.groupId())
                .query(String.class)
                .list();
    }

    private List<String> currentElo(Group g) {
        return jdbc.sql("select concat_ws('|', id, current_elo) from players where group_id = :g::uuid order by id")
                .param("g", g.groupId())
                .query(String.class)
                .list();
    }

    private long count(String matchId, String reason) {
        return jdbc.sql("select count(*) from elo_changes where match_id = :m::uuid and reason = :r")
                .param("m", matchId)
                .param("r", reason)
                .query(Long.class)
                .single();
    }

    private void assertCurrentEloMatchesLedger(Group g) {
        long mismatches = jdbc.sql("""
                        select count(*) from players p
                        where p.group_id = :g::uuid and p.current_elo <>
                          (select i.elo_after from elo_changes i where i.player_id = p.id and i.reason = 'INITIAL')
                          + coalesce((select sum(e.delta) from elo_changes e
                                      where e.player_id = p.id and e.reason <> 'INITIAL'), 0)
                        """)
                .param("g", g.groupId())
                .query(Long.class)
                .single();
        assertThat(mismatches).isZero();
    }

    private List<Integer> scoreOf(String base) {
        return jdbc.sql("select score_a, score_b from matches where id = :m::uuid")
                .param("m", matchIdOf(base))
                .query((rs, i) -> List.of(rs.getInt("score_a"), rs.getInt("score_b")))
                .single();
    }

    /** Ledger after an explicit full replay from {@code base}'s match: what incremental updates must equal. */
    private List<String> fullReplayLedger(Group g, String base) {
        replay.replayFrom(UUID.fromString(g.groupId()), UUID.fromString(matchIdOf(base)));
        return ledger(g);
    }

    @Test
    void closingALiveMatchWritesTeamResultAndBonusRows() {
        Group g = newGroup(newUser("el_admin"));
        String base = match(g, "2030-05-01T20:00:00Z", true);
        String matchId = matchIdOf(base);
        playTwoOne(g, base);

        MvcResult closed = g.admin().post(base + "/close", null);

        assertThat(status(closed)).isEqualTo(200);
        assertThat((Integer) json(closed, "$.scoreA")).isEqualTo(2);
        assertThat((Integer) json(closed, "$.scoreB")).isEqualTo(1);
        List<Object> changes = json(closed, "$.eloChanges");
        assertThat(changes).hasSize(4);
        String state = jdbc.sql("select status || '|' || score_a || '|' || score_b || '|' || (closed_at is not null)"
                        + " from matches where id = :m::uuid")
                .param("m", matchId)
                .query(String.class)
                .single();
        assertThat(state).isEqualTo("CLOSED|2|1|true");

        assertThat(count(matchId, "TEAM_RESULT")).isEqualTo(4);
        assertThat(count(matchId, "BONUS")).isEqualTo(3);
        long goalkeeperBonus = jdbc.sql(
                        "select count(*) from elo_changes where match_id = :m::uuid and reason = 'BONUS' and player_id = :p::uuid")
                .param("m", matchId)
                .param("p", g.gkB())
                .query(Long.class)
                .single();
        assertThat(goalkeeperBonus).isZero();
        long inconsistent = jdbc.sql(
                        "select count(*) from elo_changes where match_id = :m::uuid and elo_after <> elo_before + delta")
                .param("m", matchId)
                .query(Long.class)
                .single();
        assertThat(inconsistent).isZero();
        assertCurrentEloMatchesLedger(g);
    }

    @Test
    void closeIsRefusedWhenNotLiveOrNotAdmin() {
        Group g = newGroup(newUser("el_admin"));
        ApiTestClient member = joinedMember(g, "el_member");
        ApiTestClient stranger = newUser("el_stranger");
        String scheduled = match(g, "2030-05-01T20:00:00Z", false);
        String live = match(g, "2030-05-08T20:00:00Z", true);

        assertThat(status(g.admin().post(scheduled + "/close", null))).isEqualTo(409);
        assertThat(status(member.post(live + "/close", null))).isEqualTo(403);
        assertThat(status(stranger.post(live + "/close", null))).isEqualTo(404);
        assertThat(status(g.admin().post(live + "/close", Map.of("mvpPlayerId", UUID.randomUUID().toString()))))
                .isEqualTo(422);
        assertThat(status(g.admin().post(live + "/close", null))).isEqualTo(200);
        assertThat(status(g.admin().post(live + "/close", null))).isEqualTo(409);
    }

    @Test
    void annullingAClosedMatchRecomputesLaterMatchesLikeAFullReplay() {
        Group g = newGroup(newUser("el_admin"));
        String first = match(g, "2030-05-01T20:00:00Z", true);
        String second = match(g, "2030-05-08T20:00:00Z", true);
        playTwoOne(g, first);
        record(g, second, "GOAL", "B", g.outfieldB(), g.gkB());
        record(g, second, "SAVE", "A", g.adminPlayer(), null);
        assertThat(status(g.admin().post(first + "/close", null))).isEqualTo(200);
        assertThat(status(g.admin().post(second + "/close", null))).isEqualTo(200);
        List<String> initial = initialRows(g);

        assertThat(status(g.admin().post(first + "/annul", null))).isEqualTo(200);

        assertThat(count(matchIdOf(first), "TEAM_RESULT")).isZero();
        assertThat(count(matchIdOf(first), "BONUS")).isZero();
        assertThat(count(matchIdOf(second), "TEAM_RESULT")).isEqualTo(4);
        assertCurrentEloMatchesLedger(g);
        List<String> ledgerAfterAnnul = ledger(g);
        List<String> eloAfterAnnul = currentElo(g);
        replay.replayFrom(UUID.fromString(g.groupId()), UUID.fromString(matchIdOf(second)));
        assertThat(ledger(g)).isEqualTo(ledgerAfterAnnul);
        assertThat(currentElo(g)).isEqualTo(eloAfterAnnul);
        assertThat(initialRows(g)).isEqualTo(initial);
    }

    @Test
    void annulRulesDependOnMatchStatus() {
        Group g = newGroup(newUser("el_admin"));
        String scheduled = match(g, "2030-05-01T20:00:00Z", false);
        String live = match(g, "2030-05-08T20:00:00Z", true);

        assertThat(status(g.admin().post(live + "/annul", null))).isEqualTo(409);
        assertThat(status(g.admin().post(scheduled + "/annul", null))).isEqualTo(200);
        assertThat(status(g.admin().post(scheduled + "/annul", null))).isEqualTo(409);
    }

    @Test
    void correctingAClosedMatchRecomputesItAndEveryLaterOne() {
        Group g = newGroup(newUser("el_admin"));
        ApiTestClient member = joinedMember(g, "el_member");
        assertThat(status(g.admin().patch("/api/groups/" + g.groupId(), Map.of("membersCanScore", true))))
                .isEqualTo(200);
        String first = match(g, "2030-05-01T20:00:00Z", true);
        String second = match(g, "2030-05-08T20:00:00Z", true);
        playTwoOne(g, first);
        record(g, second, "GOAL", "A", g.outfieldA(), null);
        assertThat(status(g.admin().post(first + "/close", null))).isEqualTo(200);
        assertThat(status(g.admin().post(second + "/close", null))).isEqualTo(200);
        List<String> initial = initialRows(g);

        String added = record(g, first, "GOAL", "B", g.outfieldB(), g.gkB());
        assertThat(scoreOf(first)).containsExactly(2, 2);
        assertThat(ledger(g)).isEqualTo(fullReplayLedger(g, first));
        assertCurrentEloMatchesLedger(g);

        assertThat(status(g.admin().post(first + "/events/" + added + "/void", null)))
                .isEqualTo(200);
        assertThat(scoreOf(first)).containsExactly(2, 1);
        assertThat(ledger(g)).isEqualTo(fullReplayLedger(g, first));

        String memberEvent = UUID.randomUUID().toString();
        assertThat(status(member.post(first + "/events", event(memberEvent, "GOAL", "A", g.outfieldA(), null))))
                .isEqualTo(403);
        assertThat(status(member.post(first + "/events/" + added + "/void", null))).isEqualTo(403);

        assertThat(status(member.put(first + "/mvp", Map.of("mvpPlayerId", g.outfieldB()))))
                .isEqualTo(403);
        assertThat(status(g.admin().put(first + "/mvp", Map.of("mvpPlayerId", UUID.randomUUID().toString()))))
                .isEqualTo(422);
        MvcResult changed = g.admin().put(first + "/mvp", Map.of("mvpPlayerId", g.outfieldB()));
        assertThat(status(changed)).isEqualTo(200);
        assertThat((String) json(changed, "$.mvpPlayerId")).isEqualTo(g.outfieldB());
        int mvpPoints = jdbc.sql("""
                        select (details->>'mvp')::int from elo_changes
                        where match_id = :m::uuid and player_id = :p::uuid and reason = 'BONUS'
                        """)
                .param("m", matchIdOf(first))
                .param("p", g.outfieldB())
                .query(Integer.class)
                .single();
        assertThat(mvpPoints).isEqualTo(3);
        assertThat(ledger(g)).isEqualTo(fullReplayLedger(g, first));
        assertThat(initialRows(g)).isEqualTo(initial);
    }

    @Test
    void editingAPlayerAfterAReplayNeverOverwritesCurrentElo() {
        Group g = newGroup(newUser("el_admin"));
        String base = match(g, "2030-05-01T20:00:00Z", true);
        playTwoOne(g, base);
        assertThat(status(g.admin().post(base + "/close", null))).isEqualTo(200);
        UUID playerId = UUID.fromString(g.outfieldA());
        int initialElo = jdbc.sql("select elo_after from elo_changes where player_id = :p::uuid and reason = 'INITIAL'")
                .param("p", g.outfieldA())
                .query(Integer.class)
                .single();

        // Una entidad cargada antes del replay y guardada después no puede pisar el valor que escribió el replay.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            Player stale = players.findById(playerId).orElseThrow();
            assertThat(stale.getCurrentElo()).isNotEqualTo(initialElo);
            jdbc.sql("update matches set status = 'ANNULLED' where id = :m::uuid")
                    .param("m", matchIdOf(base))
                    .update();
            replay.replayFrom(UUID.fromString(g.groupId()), UUID.fromString(matchIdOf(base)));
            stale.setName("Campo A renombrado");
            players.saveAndFlush(stale);
        });

        int stored = jdbc.sql("select current_elo from players where id = :p::uuid")
                .param("p", g.outfieldA())
                .query(Integer.class)
                .single();
        assertThat(stored).isEqualTo(initialElo);
        assertThat(jdbc.sql("select name from players where id = :p::uuid")
                        .param("p", g.outfieldA())
                        .query(String.class)
                        .single())
                .isEqualTo("Campo A renombrado");
    }

    @Test
    void mvpCanOnlyBeChangedOnAClosedMatch() {
        Group g = newGroup(newUser("el_admin"));
        String live = match(g, "2030-05-01T20:00:00Z", true);

        assertThat(status(g.admin().put(live + "/mvp", Map.of("mvpPlayerId", g.outfieldA()))))
                .isEqualTo(409);
    }
}
