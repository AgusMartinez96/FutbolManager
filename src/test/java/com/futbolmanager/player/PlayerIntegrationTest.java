package com.futbolmanager.player;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
class PlayerIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private ApiTestClient newUser(String prefix) {
        ApiTestClient client = new ApiTestClient(mvc);
        assertThat(status(client.register(ApiTestClient.uniqueUsername(prefix), prefix, PASSWORD)))
                .isEqualTo(201);
        return client;
    }

    private String userId(ApiTestClient client) {
        return json(client.get("/api/auth/me"), "$.id");
    }

    private String createGroup(ApiTestClient admin) {
        MvcResult created = admin.post("/api/groups", Map.of("name", "Los Pibes"));
        assertThat(status(created)).isEqualTo(201);
        return json(created, "$.id");
    }

    private void joinGroup(ApiTestClient admin, String groupId, ApiTestClient guest) {
        String code = json(admin.get("/api/groups/" + groupId), "$.inviteCode");
        assertThat(status(guest.post("/api/groups/join", Map.of("inviteCode", code))))
                .isEqualTo(200);
    }

    private MvcResult createPlayer(ApiTestClient client, String groupId, String name) {
        return client.post("/api/groups/" + groupId + "/players", Map.of("name", name, "level", 5));
    }

    private int levelTenthsOf(String playerId) {
        return jdbc.sql("select level_tenths from players where id = :p::uuid")
                .param("p", playerId)
                .query(Integer.class)
                .single();
    }

    private List<Map<String, Object>> playerRows(String groupId, String userId) {
        return jdbc.sql("""
                        select id::text as id, name, preferred_position, dominant_foot, current_elo,
                               deleted_at is not null as deleted
                        from players where group_id = :g::uuid and user_id = :u::uuid
                        """)
                .param("g", groupId)
                .param("u", userId)
                .query()
                .listOfRows();
    }

    private List<Map<String, Object>> eloRows(String playerId) {
        return jdbc.sql("""
                        select reason, match_id, elo_before, elo_after, delta
                        from elo_changes where player_id = :p::uuid
                        """)
                .param("p", playerId)
                .query()
                .listOfRows();
    }

    private void setElo(String playerId, int elo) {
        jdbc.sql("update players set current_elo = :elo where id = :p::uuid")
                .param("elo", elo)
                .param("p", playerId)
                .update();
    }

    @Test
    void creatingAGroupCreatesTheCreatorsPlayerWithFrozenInitialElo() {
        ApiTestClient admin = newUser("ana");

        String groupId = createGroup(admin);

        List<Map<String, Object>> rows = playerRows(groupId, userId(admin));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("current_elo")).isEqualTo(1000);
        List<Map<String, Object>> ledger = eloRows((String) rows.getFirst().get("id"));
        assertThat(ledger).hasSize(1);
        Map<String, Object> initial = ledger.getFirst();
        assertThat(initial.get("reason")).isEqualTo("INITIAL");
        assertThat(initial.get("match_id")).isNull();
        assertThat(initial.get("elo_before")).isEqualTo(1000);
        assertThat(initial.get("elo_after")).isEqualTo(1000);
        assertThat(initial.get("delta")).isEqualTo(0);
    }

    @Test
    void adminCreatedPlayerStartsAtTheRoundedAverageEloOfActivePlayers() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);
        MvcResult first = createPlayer(admin, groupId, "Invitado Uno");
        assertThat(status(first)).isEqualTo(201);
        assertThat((Integer) json(first, "$.currentElo")).isEqualTo(1000);
        setElo(json(first, "$.id"), 1101);

        MvcResult second = createPlayer(admin, groupId, "Invitado Dos");

        assertThat(status(second)).isEqualTo(201);
        assertThat((Integer) json(second, "$.currentElo")).isEqualTo(1051);
        assertThat(json(second, "$.preferredPosition").toString()).isEqualTo("MID");
        assertThat(json(second, "$.dominantFoot").toString()).isEqualTo("RIGHT");
        Map<String, Object> initial = eloRows(json(second, "$.id")).getFirst();
        assertThat(initial.get("reason")).isEqualTo("INITIAL");
        assertThat(initial.get("elo_before")).isEqualTo(1051);
        assertThat(initial.get("elo_after")).isEqualTo(1051);
        assertThat(initial.get("delta")).isEqualTo(0);
    }

    @Test
    void joiningCreatesOneLinkedPlayerAndRejoiningAfterRemovalRestoresIt() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient guest = newUser("beto");
        String groupId = createGroup(admin);
        String guestId = userId(guest);

        joinGroup(admin, groupId, guest);
        joinGroup(admin, groupId, guest);

        List<Map<String, Object>> rows = playerRows(groupId, guestId);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("name")).isEqualTo("beto");
        assertThat(rows.getFirst().get("preferred_position")).isEqualTo("MID");
        assertThat(rows.getFirst().get("dominant_foot")).isEqualTo("RIGHT");
        String playerId = (String) rows.getFirst().get("id");

        assertThat(status(admin.delete("/api/groups/" + groupId + "/members/" + guestId)))
                .isEqualTo(204);
        assertThat(playerRows(groupId, guestId).getFirst().get("deleted")).isEqualTo(true);

        joinGroup(admin, groupId, guest);

        List<Map<String, Object>> restored = playerRows(groupId, guestId);
        assertThat(restored).hasSize(1);
        assertThat(restored.getFirst().get("id")).isEqualTo(playerId);
        assertThat(restored.getFirst().get("deleted")).isEqualTo(false);
        assertThat(eloRows(playerId)).hasSize(1);
    }

    @Test
    void deletingAPlayerSoftDeletesItKeepsItsLedgerAndHidesItFromTheList() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);
        MvcResult created = createPlayer(admin, groupId, "Invitado Uno");
        String playerId = json(created, "$.id");

        MvcResult deleted = admin.delete("/api/groups/" + groupId + "/players/" + playerId);

        assertThat(status(deleted)).isEqualTo(204);
        Boolean softDeleted = jdbc.sql("select deleted_at is not null from players where id = :p::uuid")
                .param("p", playerId)
                .query(Boolean.class)
                .single();
        assertThat(softDeleted).isTrue();
        assertThat(eloRows(playerId)).hasSize(1);
        List<String> ids = json(admin.get("/api/groups/" + groupId + "/players"), "$[*].id");
        assertThat(ids).doesNotContain(playerId);
    }

    @Test
    void memberCannotCreateAPlayerAndNonMemberGetsNotFound() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        ApiTestClient outsider = newUser("caro");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);

        assertThat(status(createPlayer(member, groupId, "Colado"))).isEqualTo(403);
        assertThat(status(outsider.get("/api/groups/" + groupId + "/players"))).isEqualTo(404);
        assertThat(status(member.get("/api/groups/" + groupId + "/players"))).isEqualTo(200);
    }

    @Test
    void rejectsAPreferredPositionOutsideTheAllowedSet() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);

        MvcResult result = admin.post(
                "/api/groups/" + groupId + "/players", Map.of("name", "Invitado", "preferredPosition", "STRIKER"));

        assertThat(status(result)).isEqualTo(400);
        List<String> fields = json(result, "$.errors[*].field");
        assertThat(fields).contains("preferredPosition");
    }

    @Test
    void storesDecimalLevelsAsTenthsAndEchoesThemWithOneDecimal() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);
        String url = "/api/groups/" + groupId + "/players";

        MvcResult high = admin.post(url, Map.of("name", "Alto", "level", new BigDecimal("8.5")));
        MvcResult low = admin.post(url, Map.of("name", "Bajo", "level", new BigDecimal("4.8")));

        assertThat(status(high)).isEqualTo(201);
        assertThat(status(low)).isEqualTo(201);
        assertThat(((Number) json(high, "$.level")).doubleValue()).isEqualTo(8.5);
        assertThat(((Number) json(low, "$.level")).doubleValue()).isEqualTo(4.8);
        assertThat(levelTenthsOf(json(high, "$.id"))).isEqualTo(85);
        assertThat(levelTenthsOf(json(low, "$.id"))).isEqualTo(48);
    }

    @Test
    void rejectsCreatingAPlayerWithoutLevelOrOutsideOnePointZeroToTenPointZeroInTenths() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);
        String url = "/api/groups/" + groupId + "/players";

        List<MvcResult> rejected = List.of(
                admin.post(url, Map.of("name", "Sin nivel")),
                admin.post(url, Map.of("name", "Cero", "level", 0)),
                admin.post(url, Map.of("name", "Once", "level", 11)),
                admin.post(url, Map.of("name", "Bajo", "level", new BigDecimal("0.9"))),
                admin.post(url, Map.of("name", "Alto", "level", new BigDecimal("10.1"))),
                admin.post(url, Map.of("name", "Centesimas", "level", new BigDecimal("8.55"))));

        for (MvcResult result : rejected) {
            assertThat(status(result)).isEqualTo(400);
            List<String> fields = json(result, "$.errors[*].field");
            assertThat(fields).contains("level");
        }
    }

    @Test
    void rejectsANonNumericLevel() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);

        MvcResult result =
                admin.post("/api/groups/" + groupId + "/players", Map.of("name", "Texto", "level", "abc"));

        assertThat(status(result)).isEqualTo(400);
    }

    @Test
    void databaseRejectsALevelOutsideOneToTenInTenths() {
        ApiTestClient admin = newUser("ana");
        String groupId = createGroup(admin);

        for (int tenths : new int[] {9, 101}) {
            assertThatThrownBy(() -> jdbc.sql("""
                                    insert into players (group_id, name, preferred_position, dominant_foot, current_elo, level_tenths)
                                    values (:g::uuid, 'Directo', 'MID', 'RIGHT', 1000, :tenths)
                                    """)
                            .param("g", groupId)
                            .param("tenths", tenths)
                            .update())
                    .hasMessageContaining("ck_players_level_tenths");
        }
    }

    @Test
    void adminCanChangeLevelAndMemberGetsForbiddenEvenOnOwnPlayer() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);
        String own = (String) playerRows(groupId, userId(member)).getFirst().get("id");
        String url = "/api/groups/" + groupId + "/players/" + own;

        MvcResult changed = admin.patch(url, Map.of("level", new BigDecimal("8.5")));

        assertThat(status(changed)).isEqualTo(200);
        assertThat(((Number) json(changed, "$.level")).doubleValue()).isEqualTo(8.5);
        assertThat(levelTenthsOf(own)).isEqualTo(85);
        assertThat(status(member.patch(url, Map.of("level", 9)))).isEqualTo(403);
        assertThat(levelTenthsOf(own)).isEqualTo(85);
        assertThat(status(admin.patch(url, Map.of("level", 11)))).isEqualTo(400);
        assertThat(status(admin.patch(url, Map.of("level", new BigDecimal("0.9"))))).isEqualTo(400);
        assertThat(status(admin.patch(url, Map.of("level", new BigDecimal("8.55"))))).isEqualTo(400);
        assertThat(status(admin.patch(url, Map.of("level", "abc")))).isEqualTo(400);
        assertThat(levelTenthsOf(own)).isEqualTo(85);
    }

    @Test
    void levelIsOnlyVisibleToAdmins() throws Exception {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);
        String own = (String) playerRows(groupId, userId(member)).getFirst().get("id");
        String url = "/api/groups/" + groupId + "/players";

        MvcResult asMember = member.get(url);
        MvcResult asAdmin = admin.get(url);
        MvcResult memberEdit = member.patch(url + "/" + own, Map.of("nickname", "Beto"));

        assertThat(status(asMember)).isEqualTo(200);
        assertThat(asMember.getResponse().getContentAsString()).doesNotContain("level");
        assertThat(status(memberEdit)).isEqualTo(200);
        assertThat(memberEdit.getResponse().getContentAsString()).doesNotContain("level");
        List<Number> levels = json(asAdmin, "$[*].level");
        assertThat(levels).hasSize(2).doesNotContainNull();
    }

    @Test
    void autoCreatedPlayersStartAtLevelFivePointZero() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient guest = newUser("beto");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, guest);

        String creator = (String) playerRows(groupId, userId(admin)).getFirst().get("id");
        String joined = (String) playerRows(groupId, userId(guest)).getFirst().get("id");

        assertThat(levelTenthsOf(creator)).isEqualTo(50);
        assertThat(levelTenthsOf(joined)).isEqualTo(50);
        List<Number> levels = json(admin.get("/api/groups/" + groupId + "/players"), "$[*].level");
        assertThat(levels).allSatisfy(level -> assertThat(level.doubleValue()).isEqualTo(5.0));
    }

    @Test
    void linkedMemberCanEditOwnPlayerButNotOthers() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);
        String own = (String) playerRows(groupId, userId(member)).getFirst().get("id");
        String other = json(createPlayer(admin, groupId, "Invitado"), "$.id");

        MvcResult edited = member.patch(
                "/api/groups/" + groupId + "/players/" + own, Map.of("preferredPosition", "GK"));

        assertThat(status(edited)).isEqualTo(200);
        assertThat(json(edited, "$.preferredPosition").toString()).isEqualTo("GK");
        assertThat(status(member.patch("/api/groups/" + groupId + "/players/" + other, Map.of("nickname", "X"))))
                .isEqualTo(403);
    }
}
