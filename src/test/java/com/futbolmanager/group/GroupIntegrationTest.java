package com.futbolmanager.group;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
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
class GroupIntegrationTest {

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

    private MvcResult createGroup(ApiTestClient admin) {
        MvcResult created = admin.post("/api/groups", Map.of("name", "Los Pibes"));
        assertThat(status(created)).isEqualTo(201);
        return created;
    }

    private int membershipCount(String groupId, String userId) {
        return jdbc.sql("select count(*) from memberships where group_id = :g::uuid and user_id = :u::uuid")
                .param("g", groupId)
                .param("u", userId)
                .query(Integer.class)
                .single();
    }

    private String roleOf(String groupId, String userId) {
        return jdbc.sql("select role from memberships where group_id = :g::uuid and user_id = :u::uuid")
                .param("g", groupId)
                .param("u", userId)
                .query(String.class)
                .single();
    }

    @Test
    void createReturnsInviteCodeAndMakesCreatorAdmin() {
        ApiTestClient admin = newUser("ana");

        MvcResult created = createGroup(admin);

        String code = json(created, "$.inviteCode");
        assertThat(code).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$");
        assertThat(json(created, "$.myRole").toString()).isEqualTo("ADMIN");
        assertThat(roleOf(json(created, "$.id"), userId(admin))).isEqualTo("ADMIN");
    }

    @Test
    void joinCreatesOneMemberMembershipAndRepeatedJoinIsIdempotent() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient guest = newUser("beto");
        MvcResult created = createGroup(admin);
        String groupId = json(created, "$.id");
        String code = json(created, "$.inviteCode");

        MvcResult first = guest.post("/api/groups/join", Map.of("inviteCode", code));
        MvcResult second = guest.post("/api/groups/join", Map.of("inviteCode", code));

        assertThat(status(first)).isEqualTo(200);
        assertThat(json(first, "$.groupId").toString()).isEqualTo(groupId);
        assertThat(status(second)).isEqualTo(200);
        assertThat(json(second, "$.groupId").toString()).isEqualTo(groupId);
        String guestId = userId(guest);
        assertThat(membershipCount(groupId, guestId)).isEqualTo(1);
        assertThat(roleOf(groupId, guestId)).isEqualTo("MEMBER");
    }

    @Test
    void returns404ToNonMembersOnEveryGroupEndpoint() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient outsider = newUser("carla");
        String groupId = json(createGroup(admin), "$.id");
        String adminId = userId(admin);

        assertThat(status(outsider.get("/api/groups/" + groupId))).isEqualTo(404);
        assertThat(status(outsider.get("/api/groups/" + groupId + "/members"))).isEqualTo(404);
        assertThat(status(outsider.patch("/api/groups/" + groupId, Map.of("name", "Otro"))))
                .isEqualTo(404);
        assertThat(status(outsider.delete("/api/groups/" + groupId + "/members/" + adminId)))
                .isEqualTo(404);
        assertThat(status(outsider.post("/api/groups/" + groupId + "/invite-code", Map.of())))
                .isEqualTo(404);
        assertThat(status(outsider.patch("/api/groups/" + groupId + "/members/" + adminId, Map.of("role", "MEMBER"))))
                .isEqualTo(404);
    }

    @Test
    void returns403ToMembersOnAdminOnlyEndpoints() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        MvcResult created = createGroup(admin);
        String groupId = json(created, "$.id");
        member.post("/api/groups/join", Map.of("inviteCode", json(created, "$.inviteCode")));
        String adminId = userId(admin);

        assertThat(status(member.patch("/api/groups/" + groupId, Map.of("name", "Otro"))))
                .isEqualTo(403);
        assertThat(status(member.post("/api/groups/" + groupId + "/invite-code", Map.of())))
                .isEqualTo(403);
        assertThat(status(member.patch("/api/groups/" + groupId + "/members/" + adminId, Map.of("role", "MEMBER"))))
                .isEqualTo(403);
        assertThat(status(member.delete("/api/groups/" + groupId + "/members/" + adminId)))
                .isEqualTo(403);
        assertThat(status(member.get("/api/groups/" + groupId))).isEqualTo(200);
    }

    @Test
    void returns409WhenDemotingOrRemovingTheOnlyAdmin() {
        ApiTestClient admin = newUser("ana");
        String groupId = json(createGroup(admin), "$.id");
        String adminId = userId(admin);

        assertThat(status(admin.patch("/api/groups/" + groupId + "/members/" + adminId, Map.of("role", "MEMBER"))))
                .isEqualTo(409);
        assertThat(status(admin.delete("/api/groups/" + groupId + "/members/" + adminId)))
                .isEqualTo(409);
        assertThat(roleOf(groupId, adminId)).isEqualTo("ADMIN");
    }

    @Test
    void adminCanPromoteAMemberAndThenStepDown() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        MvcResult created = createGroup(admin);
        String groupId = json(created, "$.id");
        member.post("/api/groups/join", Map.of("inviteCode", json(created, "$.inviteCode")));
        String memberId = userId(member);
        String adminId = userId(admin);

        MvcResult promoted = admin.patch("/api/groups/" + groupId + "/members/" + memberId, Map.of("role", "ADMIN"));
        assertThat(status(promoted)).isEqualTo(200);
        assertThat(json(promoted, "$.role").toString()).isEqualTo("ADMIN");
        assertThat(status(admin.patch("/api/groups/" + groupId + "/members/" + adminId, Map.of("role", "MEMBER"))))
                .isEqualTo(200);

        assertThat(roleOf(groupId, adminId)).isEqualTo("MEMBER");
        assertThat(status(member.delete("/api/groups/" + groupId + "/members/" + adminId)))
                .isEqualTo(204);
        assertThat(membershipCount(groupId, adminId)).isZero();
    }

    @Test
    void memberCanLeaveTheirOwnGroup() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient member = newUser("beto");
        MvcResult created = createGroup(admin);
        String groupId = json(created, "$.id");
        member.post("/api/groups/join", Map.of("inviteCode", json(created, "$.inviteCode")));

        assertThat(status(member.delete("/api/groups/" + groupId + "/members/" + userId(member))))
                .isEqualTo(204);

        assertThat(status(member.get("/api/groups/" + groupId))).isEqualTo(404);
    }

    @Test
    void regeneratingTheCodeInvalidatesTheOldOne() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient guest = newUser("beto");
        MvcResult created = createGroup(admin);
        String groupId = json(created, "$.id");
        String oldCode = json(created, "$.inviteCode");

        MvcResult regenerated = admin.post("/api/groups/" + groupId + "/invite-code", Map.of());

        assertThat(status(regenerated)).isEqualTo(200);
        String newCode = json(regenerated, "$.inviteCode");
        assertThat(newCode).matches("^[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{8}$").isNotEqualTo(oldCode);
        assertThat(status(guest.post("/api/groups/join", Map.of("inviteCode", oldCode))))
                .isEqualTo(404);
        assertThat(status(guest.post("/api/groups/join", Map.of("inviteCode", newCode))))
                .isEqualTo(200);
    }

    @Test
    void listsOnlyMyGroupsAndMembersWithRoles() {
        ApiTestClient admin = newUser("ana");
        ApiTestClient other = newUser("carla");
        String groupId = json(createGroup(admin), "$.id");
        createGroup(other);

        MvcResult mine = admin.get("/api/groups");
        MvcResult members = admin.get("/api/groups/" + groupId + "/members");

        assertThat(status(mine)).isEqualTo(200);
        assertThat(json(mine, "$.length()").toString()).isEqualTo("1");
        assertThat(json(mine, "$[0].id").toString()).isEqualTo(groupId);
        assertThat(json(members, "$[0].role").toString()).isEqualTo("ADMIN");
        assertThat(json(members, "$[0].userId").toString()).isEqualTo(userId(admin));
    }

    @Test
    void rejectsUnknownInviteCodeAndBlankName() {
        ApiTestClient user = newUser("ana");

        assertThat(status(user.post("/api/groups/join", Map.of("inviteCode", "ZZZZZZZZ"))))
                .isEqualTo(404);
        assertThat(status(user.post("/api/groups", Map.of("name", "   ")))).isEqualTo(400);
    }
}
