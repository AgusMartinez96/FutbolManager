package com.futbolmanager.match;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
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
class MatchIntegrationTest {

    private static final String PASSWORD = "secreto123";
    private static final String PLAYED_AT = "2030-05-01T20:00:00Z";

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

    private String createMatch(ApiTestClient admin, String groupId, String playedAt) {
        MvcResult created = admin.post(
                "/api/groups/" + groupId + "/matches", Map.of("playedAt", playedAt, "location", "Cancha 1", "format", 7));
        assertThat(status(created)).isEqualTo(201);
        return json(created, "$.id");
    }

    private String createMatch(ApiTestClient admin, String groupId) {
        return createMatch(admin, groupId, PLAYED_AT);
    }

    private String createGuestPlayer(ApiTestClient admin, String groupId, String name) {
        MvcResult created = admin.post("/api/groups/" + groupId + "/players", Map.of("name", name, "level", 5));
        assertThat(status(created)).isEqualTo(201);
        return json(created, "$.id");
    }

    private String ownPlayerId(String groupId, ApiTestClient client) {
        String userId = json(client.get("/api/auth/me"), "$.id");
        return jdbc.sql("select id::text from players where group_id = :g::uuid and user_id = :u::uuid")
                .param("g", groupId)
                .param("u", userId)
                .query(String.class)
                .single();
    }

    private void confirm(ApiTestClient admin, String groupId, String matchId, String playerId) {
        MvcResult result = admin.put(
                "/api/groups/" + groupId + "/matches/" + matchId + "/call-ups/" + playerId,
                Map.of("status", "CONFIRMED"));
        assertThat(status(result)).isEqualTo(200);
    }

    private void addConfirmedGuests(ApiTestClient admin, String groupId, String matchId, int count) {
        for (int i = 0; i < count; i++) {
            confirm(admin, groupId, matchId, createGuestPlayer(admin, groupId, "Invitado " + i));
        }
    }

    private Map<String, Object> entry(String playerId, String team, String position) {
        return Map.of("playerId", playerId, "team", team, "position", position);
    }

    private long lineupRows(String matchId) {
        return jdbc.sql("select count(*) from match_lineup_entries where match_id = :m::uuid")
                .param("m", matchId)
                .query(Long.class)
                .single();
    }

    @Test
    void adminCreatesAScheduledMatchAndRejectsAnInvalidFormat() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);

        MvcResult created = admin.post(
                "/api/groups/" + groupId + "/matches",
                Map.of("playedAt", PLAYED_AT, "location", "Cancha 1", "format", 7));
        assertThat(status(created)).isEqualTo(201);
        assertThat((String) json(created, "$.status")).isEqualTo("SCHEDULED");
        assertThat((Integer) json(created, "$.format")).isEqualTo(7);

        MvcResult invalid = admin.post(
                "/api/groups/" + groupId + "/matches",
                Map.of("playedAt", PLAYED_AT, "location", "Cancha 1", "format", 6));
        assertThat(status(invalid)).isEqualTo(400);
        List<String> fields = json(invalid, "$.errors[*].field");
        assertThat(fields).contains("format");
    }

    @Test
    void memberCannotCreateAMatch() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient member = newUser("mt_member");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);

        MvcResult result = member.post(
                "/api/groups/" + groupId + "/matches",
                Map.of("playedAt", PLAYED_AT, "location", "Cancha 1", "format", 7));
        assertThat(status(result)).isEqualTo(403);
    }

    @Test
    void memberConfirmsOwnCallUpButNotSomeoneElses() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient member = newUser("mt_member");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);
        String matchId = createMatch(admin, groupId);
        String base = "/api/groups/" + groupId + "/matches/" + matchId + "/call-ups/";

        MvcResult own = member.put(base + ownPlayerId(groupId, member), Map.of("status", "CONFIRMED"));
        assertThat(status(own)).isEqualTo(200);
        assertThat((String) json(own, "$.status")).isEqualTo("CONFIRMED");

        MvcResult other = member.put(base + ownPlayerId(groupId, admin), Map.of("status", "CONFIRMED"));
        assertThat(status(other)).isEqualTo(403);

        MvcResult changed = member.put(base + ownPlayerId(groupId, member), Map.of("status", "MAYBE"));
        assertThat(status(changed)).isEqualTo(200);
        List<String> statuses = json(admin.get("/api/groups/" + groupId + "/matches/" + matchId), "$.callUps[*].status");
        assertThat(statuses).containsExactly("MAYBE");
    }

    @Test
    void callUpForAPlayerOfAnotherGroupIsUnprocessable() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient stranger = newUser("mt_stranger");
        String groupId = createGroup(admin);
        String otherGroupId = createGroup(stranger);
        String matchId = createMatch(admin, groupId);

        MvcResult result = admin.put(
                "/api/groups/" + groupId + "/matches/" + matchId + "/call-ups/" + ownPlayerId(otherGroupId, stranger),
                Map.of("status", "CONFIRMED"));
        assertThat(status(result)).isEqualTo(422);
    }

    @Test
    void balanceReturnsTwoTeamsOfFiveWithoutWritingTheLineup() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        confirm(admin, groupId, matchId, ownPlayerId(groupId, admin));
        addConfirmedGuests(admin, groupId, matchId, 9);

        MvcResult balanced = admin.post(
                "/api/groups/" + groupId + "/matches/" + matchId + "/balance", Map.of("oneGoalkeeperPerSide", false));
        assertThat(status(balanced)).isEqualTo(200);
        assertThat((List<?>) json(balanced, "$.teamA")).hasSize(5);
        assertThat((List<?>) json(balanced, "$.teamB")).hasSize(5);
        assertThat((Number) json(balanced, "$.eloDifference")).isNotNull();
        List<String> positions = json(balanced, "$.teamA[*].position");
        assertThat(positions).hasSize(5).doesNotContainNull();
        assertThat(lineupRows(matchId)).isZero();
    }

    @Test
    void balanceRejectsMoreThan22Confirmed() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        confirm(admin, groupId, matchId, ownPlayerId(groupId, admin));
        addConfirmedGuests(admin, groupId, matchId, 22);

        MvcResult result = admin.post("/api/groups/" + groupId + "/matches/" + matchId + "/balance", Map.of());
        assertThat(status(result)).isEqualTo(400);
        assertThat(lineupRows(matchId)).isZero();
    }

    @Test
    void balanceNeedsAtLeastTwoConfirmed() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        confirm(admin, groupId, matchId, ownPlayerId(groupId, admin));

        MvcResult result = admin.post("/api/groups/" + groupId + "/matches/" + matchId + "/balance", Map.of());
        assertThat(status(result)).isEqualTo(400);
    }

    @Test
    void lineupWithForeignOrDeletedPlayerIsRejectedAndKeepsThePreviousLineup() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient stranger = newUser("mt_stranger");
        String groupId = createGroup(admin);
        String otherGroupId = createGroup(stranger);
        String matchId = createMatch(admin, groupId);
        String url = "/api/groups/" + groupId + "/matches/" + matchId + "/lineup";
        String me = ownPlayerId(groupId, admin);
        String guest = createGuestPlayer(admin, groupId, "Invitado");
        String doomed = createGuestPlayer(admin, groupId, "Borrado");
        assertThat(status(admin.put(url, Map.of("entries", List.of(entry(me, "A", "MID"), entry(guest, "B", "MID"))))))
                .isEqualTo(200);
        assertThat(lineupRows(matchId)).isEqualTo(2);

        String foreign = ownPlayerId(otherGroupId, stranger);
        MvcResult withForeign =
                admin.put(url, Map.of("entries", List.of(entry(me, "A", "MID"), entry(foreign, "B", "MID"))));
        assertThat(status(withForeign)).isEqualTo(422);

        assertThat(status(admin.delete("/api/groups/" + groupId + "/players/" + doomed)))
                .isEqualTo(204);
        MvcResult withDeleted =
                admin.put(url, Map.of("entries", List.of(entry(me, "A", "MID"), entry(doomed, "B", "MID"))));
        assertThat(status(withDeleted)).isEqualTo(422);

        assertThat(lineupRows(matchId)).isEqualTo(2);
        List<String> kept = json(admin.get("/api/groups/" + groupId + "/matches/" + matchId), "$.lineup[*].playerId");
        assertThat(kept).containsExactlyInAnyOrder(me, guest);
    }

    @Test
    void savingALineupReplacesThePreviousOneAndRejectsDuplicates() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        String url = "/api/groups/" + groupId + "/matches/" + matchId + "/lineup";
        String me = ownPlayerId(groupId, admin);
        String guest = createGuestPlayer(admin, groupId, "Invitado");
        assertThat(status(admin.put(url, Map.of("entries", List.of(entry(me, "A", "GK"), entry(guest, "B", "FWD"))))))
                .isEqualTo(200);

        MvcResult replaced = admin.put(url, Map.of("entries", List.of(entry(me, "B", "DEF"), entry(guest, "A", "MID"))));
        assertThat(status(replaced)).isEqualTo(200);
        assertThat(lineupRows(matchId)).isEqualTo(2);

        MvcResult duplicated = admin.put(url, Map.of("entries", List.of(entry(me, "A", "MID"), entry(me, "B", "MID"))));
        assertThat(status(duplicated)).isEqualTo(422);
        assertThat(lineupRows(matchId)).isEqualTo(2);
    }

    @Test
    void startNeedsALineupWithBothTeamsAndOnlyWorksOnce() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        String base = "/api/groups/" + groupId + "/matches/" + matchId;
        String me = ownPlayerId(groupId, admin);
        String guest = createGuestPlayer(admin, groupId, "Invitado");

        assertThat(status(admin.post(base + "/start", null))).isEqualTo(409);

        assertThat(status(admin.put(base + "/lineup", Map.of("entries", List.of(entry(me, "A", "MID"))))))
                .isEqualTo(200);
        assertThat(status(admin.post(base + "/start", null))).isEqualTo(409);

        Map<String, Object> both = Map.of("entries", List.of(entry(me, "A", "MID"), entry(guest, "B", "MID")));
        assertThat(status(admin.put(base + "/lineup", both))).isEqualTo(200);

        MvcResult started = admin.post(base + "/start", null);
        assertThat(status(started)).isEqualTo(200);
        assertThat((String) json(started, "$.status")).isEqualTo("LIVE");
        assertThat(status(admin.post(base + "/start", null))).isEqualTo(409);
        assertThat(status(admin.put(base + "/lineup", both))).isEqualTo(409);
    }

    @Test
    void nonMemberGetsNotFoundOnEveryMatchEndpoint() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient stranger = newUser("mt_stranger");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        String base = "/api/groups/" + groupId + "/matches";

        assertThat(status(stranger.get(base + "/" + matchId))).isEqualTo(404);
        assertThat(status(stranger.get(base))).isEqualTo(404);
        assertThat(status(stranger.post(base + "/" + matchId + "/start", null))).isEqualTo(404);

        String otherGroupId = createGroup(stranger);
        assertThat(status(stranger.get("/api/groups/" + otherGroupId + "/matches/" + matchId)))
                .isEqualTo(404);
    }

    @Test
    void membersSeeTheMatchListNewestFirst() {
        ApiTestClient admin = newUser("mt_admin");
        ApiTestClient member = newUser("mt_member");
        String groupId = createGroup(admin);
        joinGroup(admin, groupId, member);
        String older = createMatch(admin, groupId, "2030-01-01T20:00:00Z");
        String newer = createMatch(admin, groupId, "2030-06-01T20:00:00Z");

        MvcResult list = member.get("/api/groups/" + groupId + "/matches");
        assertThat(status(list)).isEqualTo(200);
        List<String> ids = json(list, "$[*].id");
        assertThat(ids).containsExactly(newer, older);
    }

    @Test
    void scheduledMatchCanBeEditedButFormatIsValidated() {
        ApiTestClient admin = newUser("mt_admin");
        String groupId = createGroup(admin);
        String matchId = createMatch(admin, groupId);
        String url = "/api/groups/" + groupId + "/matches/" + matchId;

        MvcResult updated = admin.patch(url, Map.of("location", "Cancha 2", "format", 5));
        assertThat(status(updated)).isEqualTo(200);
        assertThat((String) json(updated, "$.location")).isEqualTo("Cancha 2");
        assertThat((Integer) json(updated, "$.format")).isEqualTo(5);

        assertThat(status(admin.patch(url, Map.of("format", 9)))).isEqualTo(400);
    }
}
