package com.futbolmanager.elo;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Cerrar partidos uno por uno y recalcular todo desde el primero tiene que dar exactamente el mismo libro. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EloReplayPropertyTest {

    private static final String PASSWORD = "secreto123";
    private static final int PLAYERS = 12;
    private static final int MATCHES = 8;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    EloReplayService replay;

    @Test
    void replayFromTheFirstMatchReproducesTheLedgerBuiltByClosingOneByOne() {
        Random random = new Random(42);
        ApiTestClient admin = new ApiTestClient(mvc);
        assertThat(status(admin.register(ApiTestClient.uniqueUsername("pr_admin"), "pr_admin", PASSWORD)))
                .isEqualTo(201);
        String groupId = json(admin.post("/api/groups", Map.of("name", "Property")), "$.id");
        String userId = json(admin.get("/api/auth/me"), "$.id");
        List<String> players = new ArrayList<>();
        players.add(jdbc.sql("select id::text from players where group_id = :g::uuid and user_id = :u::uuid")
                .param("g", groupId)
                .param("u", userId)
                .query(String.class)
                .single());
        for (int i = 1; i < PLAYERS; i++) {
            MvcResult created = admin.post(
                    "/api/groups/" + groupId + "/players", Map.of("name", "Jugador " + i, "level", 1 + i % 9));
            assertThat(status(created)).isEqualTo(201);
            players.add(json(created, "$.id"));
        }

        String firstMatchId = null;
        for (int i = 0; i < MATCHES; i++) {
            String matchId = json(
                    admin.post(
                            "/api/groups/" + groupId + "/matches",
                            Map.of(
                                    "playedAt",
                                    "2031-03-%02dT20:00:00Z".formatted(i + 1),
                                    "location",
                                    "Cancha " + i,
                                    "format",
                                    7)),
                    "$.id");
            firstMatchId = firstMatchId == null ? matchId : firstMatchId;
            String base = "/api/groups/" + groupId + "/matches/" + matchId;
            List<String> shuffled = new ArrayList<>(players);
            Collections.shuffle(shuffled, random);
            List<String> teamA = shuffled.subList(0, PLAYERS / 2);
            List<String> teamB = shuffled.subList(PLAYERS / 2, PLAYERS);
            assertThat(status(admin.put(base + "/lineup", Map.of("entries", lineup(teamA, teamB)))))
                    .isEqualTo(200);
            assertThat(status(admin.post(base + "/start", null))).isEqualTo(200);
            int events = 4 + random.nextInt(9);
            for (int e = 0; e < events; e++) {
                recordRandomEvent(admin, base, random, teamA, teamB);
            }
            Object body = random.nextBoolean() ? Map.of("mvpPlayerId", shuffled.get(random.nextInt(PLAYERS))) : null;
            assertThat(status(admin.post(base + "/close", body))).isEqualTo(200);
        }

        List<String> ledgerBefore = ledger(groupId);
        List<String> eloBefore = currentElo(groupId);
        assertThat(ledgerBefore).isNotEmpty();
        assertThat(ledgerBefore).anyMatch(row -> row.contains("|BONUS|"));

        replay.replayFrom(UUID.fromString(groupId), UUID.fromString(firstMatchId));

        assertThat(ledger(groupId)).isEqualTo(ledgerBefore);
        assertThat(currentElo(groupId)).isEqualTo(eloBefore);
    }

    private static List<Map<String, Object>> lineup(List<String> teamA, List<String> teamB) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (List<String> team : List.of(teamA, teamB)) {
            String name = team == teamA ? "A" : "B";
            for (int i = 0; i < team.size(); i++) {
                entries.add(Map.of("playerId", team.get(i), "team", name, "position", i == 0 ? "GK" : "MID"));
            }
        }
        return entries;
    }

    private static void recordRandomEvent(
            ApiTestClient admin, String base, Random random, List<String> teamA, List<String> teamB) {
        boolean a = random.nextBoolean();
        String team = a ? "A" : "B";
        List<String> own = a ? teamA : teamB;
        String type = List.of("GOAL", "SAVE", "TACKLE", "PLAY").get(random.nextInt(4));
        String author = own.get(random.nextInt(own.size()));
        String assist = null;
        if ("GOAL".equals(type)) {
            if (random.nextInt(6) == 0) {
                author = null;
            } else if (random.nextBoolean()) {
                do {
                    assist = own.get(random.nextInt(own.size()));
                } while (assist.equals(author));
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("id", UUID.randomUUID().toString());
        body.put("type", type);
        body.put("team", team);
        body.put("playerId", author);
        body.put("assistPlayerId", assist);
        assertThat(status(admin.post(base + "/events", body))).isEqualTo(201);
    }

    private List<String> ledger(String groupId) {
        return jdbc.sql("""
                        select concat_ws('|', match_id, player_id, reason, elo_before, elo_after, delta)
                        from elo_changes where group_id = :g::uuid and reason <> 'INITIAL'
                        order by match_id, player_id, reason
                        """)
                .param("g", groupId)
                .query(String.class)
                .list();
    }

    private List<String> currentElo(String groupId) {
        return jdbc.sql("select concat_ws('|', id, current_elo) from players where group_id = :g::uuid order by id")
                .param("g", groupId)
                .query(String.class)
                .list();
    }
}
