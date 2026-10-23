package com.futbolmanager.auth;

import static com.futbolmanager.support.ApiTestClient.body;
import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import jakarta.servlet.http.Cookie;
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
class AuthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    private int countUsers(String username) {
        return jdbc.sql("select count(*) from users where lower(username) = lower(:u)")
                .param("u", username)
                .query(Integer.class)
                .single();
    }

    @Test
    void registerCreatesUserStoresBcryptHashAndStartsSession() {
        ApiTestClient client = new ApiTestClient(mvc);
        String username = ApiTestClient.uniqueUsername("ana");

        MvcResult result = client.register(username, "Ana", "secreto123");

        assertThat(status(result)).isEqualTo(201);
        assertThat(json(result, "$.username").toString()).isEqualTo(username);
        assertThat(json(result, "$.displayName").toString()).isEqualTo("Ana");
        assertThat(json(result, "$.id").toString()).isNotBlank();
        assertThat(body(result)).doesNotContain("secreto123").doesNotContain("password");
        assertThat(client.sessionCookie()).isNotNull();
        String hash = jdbc.sql("select password_hash from users where username = :u")
                .param("u", username)
                .query(String.class)
                .single();
        assertThat(hash).startsWith("$2").isNotEqualTo("secreto123");

        MvcResult me = client.get("/api/auth/me");
        assertThat(status(me)).isEqualTo(200);
        assertThat(json(me, "$.username").toString()).isEqualTo(username);
    }

    @Test
    void registerRejectsDuplicateUsernameIgnoringCase() {
        String username = ApiTestClient.uniqueUsername("Beto");
        assertThat(status(new ApiTestClient(mvc).register(username, "Beto", "secreto123")))
                .isEqualTo(201);

        MvcResult dup = new ApiTestClient(mvc).register(username.toUpperCase(), "Otro", "secreto123");

        assertThat(status(dup)).isEqualTo(409);
        assertThat(json(dup, "$.detail").toString()).isEqualTo("Ese nombre de usuario ya está en uso.");
        assertThat(countUsers(username)).isEqualTo(1);
    }

    @Test
    void registerRejectsInvalidUsernameAndPasswordWithFieldErrors() {
        MvcResult result = new ApiTestClient(mvc).register("ab", "Cami", "1234567");

        assertThat(status(result)).isEqualTo(400);
        List<String> fields = json(result, "$.errors[*].field");
        assertThat(fields).contains("username", "password");
        assertThat(json(result, "$.detail").toString()).isEqualTo("Hay datos inválidos.");
    }

    @Test
    void loginRejectsWrongPasswordAndAcceptsTheRightOne() {
        String username = ApiTestClient.uniqueUsername("dani");
        new ApiTestClient(mvc).register(username, "Dani", "secreto123");
        ApiTestClient client = new ApiTestClient(mvc);

        MvcResult wrong = client.login(username, "equivocada1");
        assertThat(status(wrong)).isEqualTo(401);
        assertThat(json(wrong, "$.detail").toString()).isEqualTo("Usuario o contraseña incorrectos.");

        MvcResult unknown = client.login(ApiTestClient.uniqueUsername("nadie"), "secreto123");
        assertThat(status(unknown)).isEqualTo(401);
        assertThat(json(unknown, "$.detail").toString()).isEqualTo("Usuario o contraseña incorrectos.");

        MvcResult ok = client.login(username.toUpperCase(), "secreto123");
        assertThat(status(ok)).isEqualTo(200);
        MvcResult me = client.get("/api/auth/me");
        assertThat(status(me)).isEqualTo(200);
        assertThat(json(me, "$.username").toString()).isEqualTo(username);
    }

    @Test
    void loginWithoutCsrfTokenIsForbidden() {
        MvcResult result = new ApiTestClient(mvc)
                .postWithoutCsrf("/api/auth/login", Map.of("username", "x", "password", "y"));

        assertThat(status(result)).isEqualTo(403);
    }

    @Test
    void logoutInvalidatesTheSession() {
        ApiTestClient client = new ApiTestClient(mvc);
        client.register(ApiTestClient.uniqueUsername("eli"), "Eli", "secreto123");
        Cookie previous = client.sessionCookie();
        assertThat(status(client.get("/api/auth/me"))).isEqualTo(200);

        assertThat(status(client.post("/api/auth/logout", null))).isEqualTo(204);

        ApiTestClient replay = new ApiTestClient(mvc);
        replay.useSession(previous);
        assertThat(status(replay.get("/api/auth/me"))).isEqualTo(401);
    }

    @Test
    void csrfEndpointReturnsHeaderNameAndToken() {
        MvcResult result = new ApiTestClient(mvc).get("/api/auth/csrf");

        assertThat(status(result)).isEqualTo(200);
        assertThat(json(result, "$.headerName").toString()).isNotBlank();
        assertThat(json(result, "$.token").toString()).isNotBlank();
    }
}
