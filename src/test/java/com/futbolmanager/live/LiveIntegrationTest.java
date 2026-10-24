package com.futbolmanager.live;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LiveIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    /** Team A: the admin (GK) and "Campo A"; team B: "Arquero B" (GK) and "Campo B". */
    private record Fixture(
            ApiTestClient admin,
            String groupId,
            String matchId,
            String adminPlayer,
            String gkB,
            String outfieldA,
            String outfieldB) {

        String base() {
            return "/api/groups/" + groupId + "/matches/" + matchId;
        }
    }

    private ApiTestClient newUser(String prefix) {
        ApiTestClient client = new ApiTestClient(mvc);
        assertThat(status(client.register(ApiTestClient.uniqueUsername(prefix), prefix, PASSWORD)))
                .isEqualTo(201);
        return client;
    }

    private String guest(ApiTestClient admin, String groupId, String name) {
        MvcResult created = admin.post("/api/groups/" + groupId + "/players", Map.of("name", name));
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

    private Map<String, Object> entry(String playerId, String team, String position) {
        return Map.of("playerId", playerId, "team", team, "position", position);
    }

    private Fixture liveMatch(ApiTestClient admin, boolean start) {
        String groupId = json(admin.post("/api/groups", Map.of("name", "Los Pibes")), "$.id");
        MvcResult created = admin.post(
                "/api/groups/" + groupId + "/matches",
                Map.of("playedAt", "2030-05-01T20:00:00Z", "location", "Cancha 1", "format", 7));
        String matchId = json(created, "$.id");
        String me = ownPlayerId(groupId, admin);
        String gkB = guest(admin, groupId, "Arquero B");
        String outfieldA = guest(admin, groupId, "Campo A");
        String outfieldB = guest(admin, groupId, "Campo B");
        String base = "/api/groups/" + groupId + "/matches/" + matchId;
        MvcResult saved = admin.put(
                base + "/lineup",
                Map.of(
                        "entries",
                        List.of(
                                entry(me, "A", "GK"),
                                entry(outfieldA, "A", "MID"),
                                entry(gkB, "B", "GK"),
                                entry(outfieldB, "B", "FWD"))));
        assertThat(status(saved)).isEqualTo(200);
        if (start) {
            assertThat(status(admin.post(base + "/start", null))).isEqualTo(200);
        }
        return new Fixture(admin, groupId, matchId, me, gkB, outfieldA, outfieldB);
    }

    private void join(Fixture f, ApiTestClient member) {
        String code = json(f.admin().get("/api/groups/" + f.groupId()), "$.inviteCode");
        assertThat(status(member.post("/api/groups/join", Map.of("inviteCode", code))))
                .isEqualTo(200);
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

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private MvcResult live(ApiTestClient client, Fixture f) {
        MvcResult result = client.get(f.base() + "/live");
        assertThat(status(result)).isEqualTo(200);
        return result;
    }

    private long eventRows(Fixture f) {
        return jdbc.sql("select count(*) from match_events where match_id = :m::uuid")
                .param("m", f.matchId())
                .query(Long.class)
                .single();
    }

    @Test
    void goalIsIdempotentByClientIdAndConflictsOnDifferentPayload() {
        ApiTestClient admin = newUser("lv_admin");
        Fixture f = liveMatch(admin, true);
        String id = newId();
        Map<String, Object> goal = event(id, "GOAL", "A", f.outfieldA(), f.adminPlayer());

        MvcResult first = admin.post(f.base() + "/events", goal);
        assertThat(status(first)).isEqualTo(201);
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isEqualTo(1);

        MvcResult replay = admin.post(f.base() + "/events", goal);
        assertThat(status(replay)).isEqualTo(200);
        assertThat((String) json(replay, "$.id")).isEqualTo(id);
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isEqualTo(1);
        assertThat(eventRows(f)).isEqualTo(1);

        MvcResult different = admin.post(f.base() + "/events", event(id, "GOAL", "B", f.outfieldB(), null));
        assertThat(status(different)).isEqualTo(409);
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isEqualTo(1);
        assertThat((Integer) json(live(admin, f), "$.scoreB")).isZero();
    }

    @Test
    void ownGoalWithoutAuthorCountsForTheBeneficiaryTeam() {
        ApiTestClient admin = newUser("lv_admin");
        Fixture f = liveMatch(admin, true);

        assertThat(status(admin.post(f.base() + "/events", event(newId(), "GOAL", "B", null, null))))
                .isEqualTo(201);
        assertThat((Integer) json(live(admin, f), "$.scoreB")).isEqualTo(1);
    }

    @Test
    void saveMustComeFromThatTeamsGoalkeeperAndTackleFromItsLineup() {
        ApiTestClient admin = newUser("lv_admin");
        Fixture f = liveMatch(admin, true);
        String url = f.base() + "/events";

        assertThat(status(admin.post(url, event(newId(), "SAVE", "A", f.outfieldA(), null))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "SAVE", "A", f.gkB(), null))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "SAVE", "A", null, null))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "TACKLE", "A", f.outfieldB(), null))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "GOAL", "A", f.outfieldB(), null))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "GOAL", "A", f.outfieldA(), f.outfieldA()))))
                .isEqualTo(422);
        assertThat(status(admin.post(url, event(newId(), "GOAL", "A", null, f.outfieldA()))))
                .isEqualTo(422);
        assertThat(eventRows(f)).isZero();

        assertThat(status(admin.post(url, event(newId(), "SAVE", "A", f.adminPlayer(), null))))
                .isEqualTo(201);
        assertThat(status(admin.post(url, event(newId(), "TACKLE", "B", f.outfieldB(), null))))
                .isEqualTo(201);
    }

    @Test
    void memberRecordsOnlyWhenTheGroupAllowsIt() {
        ApiTestClient admin = newUser("lv_admin");
        ApiTestClient member = newUser("lv_member");
        Fixture f = liveMatch(admin, true);
        join(f, member);
        String url = f.base() + "/events";

        assertThat(status(member.post(url, event(newId(), "GOAL", "A", f.outfieldA(), null))))
                .isEqualTo(403);
        assertThat((Boolean) json(live(member, f), "$.canRecord")).isFalse();

        assertThat(status(admin.patch("/api/groups/" + f.groupId(), Map.of("membersCanScore", true))))
                .isEqualTo(200);
        assertThat((Boolean) json(live(member, f), "$.canRecord")).isTrue();
        assertThat((Boolean) json(live(member, f), "$.isAdmin")).isFalse();
        assertThat(status(member.post(url, event(newId(), "GOAL", "A", f.outfieldA(), null))))
                .isEqualTo(201);
    }

    @Test
    void authorVoidsOwnEventAdminVoidsAnyAndOtherMembersAreRefused() {
        ApiTestClient admin = newUser("lv_admin");
        ApiTestClient author = newUser("lv_author");
        ApiTestClient other = newUser("lv_other");
        Fixture f = liveMatch(admin, true);
        join(f, author);
        join(f, other);
        assertThat(status(admin.patch("/api/groups/" + f.groupId(), Map.of("membersCanScore", true))))
                .isEqualTo(200);

        String mine = newId();
        assertThat(status(author.post(f.base() + "/events", event(mine, "GOAL", "A", f.outfieldA(), null))))
                .isEqualTo(201);
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isEqualTo(1);

        assertThat(status(other.post(f.base() + "/events/" + mine + "/void", null)))
                .isEqualTo(403);
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isEqualTo(1);

        MvcResult voided = author.post(f.base() + "/events/" + mine + "/void", null);
        assertThat(status(voided)).isEqualTo(200);
        assertThat((Object) json(voided, "$.voidedAt")).isNotNull();
        assertThat((Integer) json(live(admin, f), "$.scoreA")).isZero();
        assertThat(status(author.post(f.base() + "/events/" + mine + "/void", null)))
                .isEqualTo(200);

        String theirs = newId();
        assertThat(status(author.post(f.base() + "/events", event(theirs, "GOAL", "B", f.outfieldB(), null))))
                .isEqualTo(201);
        assertThat(status(admin.post(f.base() + "/events/" + theirs + "/void", null)))
                .isEqualTo(200);
        assertThat((Integer) json(live(admin, f), "$.scoreB")).isZero();
        assertThat(status(admin.post(f.base() + "/events/" + newId() + "/void", null)))
                .isEqualTo(404);
    }

    @Test
    void scheduledMatchRejectsEvents() {
        ApiTestClient admin = newUser("lv_admin");
        Fixture f = liveMatch(admin, false);

        assertThat(status(admin.post(f.base() + "/events", event(newId(), "GOAL", "A", f.outfieldA(), null))))
                .isEqualTo(409);
        assertThat(eventRows(f)).isZero();
    }

    @Test
    void liveStateListsEventsInCreationOrderWithRecorderNames() {
        ApiTestClient admin = newUser("lv_admin");
        Fixture f = liveMatch(admin, true);
        String first = newId();
        String second = newId();
        String third = newId();
        assertThat(status(admin.post(f.base() + "/events", event(first, "GOAL", "A", f.outfieldA(), f.adminPlayer()))))
                .isEqualTo(201);
        assertThat(status(admin.post(f.base() + "/events", event(second, "SAVE", "B", f.gkB(), null))))
                .isEqualTo(201);
        assertThat(status(admin.post(f.base() + "/events", event(third, "TACKLE", "A", f.outfieldA(), null))))
                .isEqualTo(201);

        MvcResult state = live(admin, f);
        List<String> ids = json(state, "$.events[*].id");
        assertThat(ids).containsExactly(first, second, third);
        List<String> recorders = json(state, "$.events[*].recordedBy.displayName");
        assertThat(recorders).containsOnly("lv_admin");
        assertThat((String) json(state, "$.events[0].playerName")).isEqualTo("Campo A");
        assertThat((String) json(state, "$.status")).isEqualTo("LIVE");
        assertThat((Boolean) json(state, "$.canRecord")).isTrue();
    }

    @Test
    void nonMemberGetsNotFoundOnEveryLiveEndpoint() {
        ApiTestClient admin = newUser("lv_admin");
        ApiTestClient stranger = newUser("lv_stranger");
        Fixture f = liveMatch(admin, true);
        String eventId = newId();
        assertThat(status(admin.post(f.base() + "/events", event(eventId, "GOAL", "A", f.outfieldA(), null))))
                .isEqualTo(201);

        assertThat(status(stranger.get(f.base() + "/live"))).isEqualTo(404);
        assertThat(status(stranger.post(f.base() + "/events", event(newId(), "GOAL", "A", null, null))))
                .isEqualTo(404);
        assertThat(status(stranger.post(f.base() + "/events/" + eventId + "/void", null)))
                .isEqualTo(404);
    }
}
