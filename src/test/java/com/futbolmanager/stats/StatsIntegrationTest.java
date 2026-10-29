package com.futbolmanager.stats;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import java.util.Comparator;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatsIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    /** Team A: the admin (observed player) and "Campo A"; team B: "Arquero B" and "Campo B". */
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

    private Group newGroup() {
        ApiTestClient admin = newUser("stats");
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

    private Map<String, Object> entry(String playerId, String team, String position) {
        return Map.of("playerId", playerId, "team", team, "position", position);
    }

    /** Creates and starts a match with the group's fixed lineup. Returns its API path. */
    private String liveMatch(Group g, String playedAt) {
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
        assertThat(status(g.admin().post(base + "/start", null))).isEqualTo(200);
        return base;
    }

    private String goal(Group g, String base, String team, String playerId, String assistId) {
        Map<String, Object> body = new HashMap<>();
        String id = UUID.randomUUID().toString();
        body.put("id", id);
        body.put("type", "GOAL");
        body.put("team", team);
        body.put("playerId", playerId);
        body.put("assistPlayerId", assistId);
        assertThat(status(g.admin().post(base + "/events", body))).isEqualTo(201);
        return id;
    }

    private void close(Group g, String base) {
        assertThat(status(g.admin().post(base + "/close", null))).isEqualTo(200);
    }

    private String statsPath(Group g, String playerId, String suffix) {
        return "/api/groups/" + g.groupId() + "/players/" + playerId + "/" + suffix;
    }

    /** Win (assisted goal), win (one goal voided), draw, plus a closed-then-annulled match full of goals. */
    private Group playedGroup() {
        Group g = newGroup();

        String m1 = liveMatch(g, "2026-03-01T20:00:00Z");
        goal(g, m1, "A", g.outfieldA(), g.adminPlayer());
        close(g, m1);

        String m2 = liveMatch(g, "2026-03-08T20:00:00Z");
        goal(g, m2, "A", g.adminPlayer(), null);
        String voided = goal(g, m2, "A", g.adminPlayer(), null);
        assertThat(status(g.admin().post(m2 + "/events/" + voided + "/void", null)))
                .isEqualTo(200);
        close(g, m2);

        String m3 = liveMatch(g, "2026-03-15T20:00:00Z");
        goal(g, m3, "A", g.adminPlayer(), null);
        goal(g, m3, "B", g.outfieldB(), null);
        close(g, m3);

        String annulled = liveMatch(g, "2026-03-22T20:00:00Z");
        goal(g, annulled, "A", g.adminPlayer(), g.outfieldA());
        goal(g, annulled, "A", g.adminPlayer(), g.outfieldA());
        close(g, annulled);
        assertThat(status(g.admin().post(annulled + "/annul", null))).isEqualTo(200);
        return g;
    }

    @Test
    void rankingOrdersByEloThenNameAndFlagsProvisionalPlayers() {
        Group g = playedGroup();

        MvcResult ranking = g.admin().get("/api/groups/" + g.groupId() + "/ranking");

        assertThat(status(ranking)).isEqualTo(200);
        List<Integer> elos = json(ranking, "$[*].currentElo");
        List<String> names = json(ranking, "$[*].name");
        assertThat(elos).hasSize(4).isSortedAccordingTo(Comparator.reverseOrder());
        for (int i = 1; i < elos.size(); i++) {
            if (elos.get(i).equals(elos.get(i - 1))) {
                assertThat(names.get(i)).isGreaterThan(names.get(i - 1));
            }
        }
        List<Integer> played = json(ranking, "$[?(@.playerId=='" + g.adminPlayer() + "')].played");
        assertThat(played).containsExactly(3);
        List<Boolean> provisional = json(ranking, "$[*].provisional");
        assertThat(provisional).containsOnly(true);
    }

    @Test
    void statsCountResultsStreakAndIgnoreVoidedEventsAndAnnulledMatches() {
        Group g = playedGroup();

        MvcResult stats = g.admin().get(statsPath(g, g.adminPlayer(), "stats"));

        assertThat(status(stats)).isEqualTo(200);
        assertThat((Integer) json(stats, "$.played")).isEqualTo(3);
        assertThat((Integer) json(stats, "$.won")).isEqualTo(2);
        assertThat((Integer) json(stats, "$.drawn")).isEqualTo(1);
        assertThat((Integer) json(stats, "$.lost")).isZero();
        assertThat((Integer) json(stats, "$.goals")).isEqualTo(2);
        assertThat((Integer) json(stats, "$.assists")).isEqualTo(1);
        assertThat((String) json(stats, "$.streak.type")).isEqualTo("D");
        assertThat((Integer) json(stats, "$.streak.length")).isEqualTo(1);
        assertThat((Boolean) json(stats, "$.provisional")).isTrue();
    }

    @Test
    void assistsFromAnAnnulledMatchDoNotCount() {
        Group g = playedGroup();

        MvcResult stats = g.admin().get(statsPath(g, g.outfieldA(), "stats"));

        assertThat((Integer) json(stats, "$.goals")).isEqualTo(1);
        assertThat((Integer) json(stats, "$.assists")).isZero();
    }

    @Test
    void streakIsNullForAPlayerWithoutClosedMatches() {
        Group g = newGroup();

        MvcResult stats = g.admin().get(statsPath(g, g.outfieldA(), "stats"));

        assertThat(status(stats)).isEqualTo(200);
        assertThat((Integer) json(stats, "$.played")).isZero();
        assertThat((Object) json(stats, "$.streak")).isNull();
    }

    @Test
    void eloHistoryStartsWithInitialAndAddsOnePointPerClosedMatch() {
        Group g = playedGroup();

        MvcResult history = g.admin().get(statsPath(g, g.adminPlayer(), "elo-history"));
        MvcResult stats = g.admin().get(statsPath(g, g.adminPlayer(), "stats"));

        assertThat(status(history)).isEqualTo(200);
        List<Object> matchIds = json(history, "$[*].matchId");
        assertThat(matchIds).hasSize(4);
        assertThat(matchIds.get(0)).isNull();
        assertThat((Integer) json(history, "$[0].eloAfter")).isEqualTo(1000);
        assertThat((Integer) json(history, "$[3].eloAfter")).isEqualTo(json(stats, "$.currentElo"));
    }

    @Test
    void returns404WhenUserIsNotAMember() {
        Group g = playedGroup();
        ApiTestClient outsider = newUser("outsider");

        assertThat(status(outsider.get("/api/groups/" + g.groupId() + "/ranking")))
                .isEqualTo(404);
        assertThat(status(outsider.get(statsPath(g, g.adminPlayer(), "stats")))).isEqualTo(404);
        assertThat(status(outsider.get(statsPath(g, g.adminPlayer(), "elo-history"))))
                .isEqualTo(404);
    }

    @Test
    void returns404ForAPlayerOfAnotherGroup() {
        Group g = newGroup();
        Group other = newGroup();

        assertThat(status(g.admin().get(statsPath(g, other.adminPlayer(), "stats"))))
                .isEqualTo(404);
    }
}
