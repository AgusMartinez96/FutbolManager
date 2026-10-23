package com.futbolmanager.auth;

import static com.futbolmanager.support.ApiTestClient.body;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "fm.auth.register-per-ip-per-hour=10")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthThrottleIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    private String registerUser(String ip) {
        String username = ApiTestClient.uniqueUsername("thr");
        MvcResult result = new ApiTestClient(mvc).fromIp(ip).register(username, "Usuario", PASSWORD);
        assertThat(status(result)).isEqualTo(201);
        return username;
    }

    private void failLogins(ApiTestClient client, String username, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(status(client.login(username, "incorrecta1"))).isEqualTo(401);
        }
    }

    @Test
    void blockedKeyGets429WithRetryAfterEvenWithTheCorrectPassword() {
        String ip = "10.1.0.1";
        String username = registerUser("10.1.0.99");
        ApiTestClient client = new ApiTestClient(mvc).fromIp(ip);
        failLogins(client, username, 5);

        MvcResult blocked = client.login(username, PASSWORD);

        assertThat(status(blocked)).isEqualTo(429);
        String retryAfter = blocked.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).matches("\\d+");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 30L);
        assertThat(body(blocked)).contains("Demasiados intentos fallidos");
    }

    @Test
    void blockedUserCanStillLogInFromAnotherIp() {
        String username = registerUser("10.2.0.99");
        failLogins(new ApiTestClient(mvc).fromIp("10.2.0.1"), username, 5);

        MvcResult result = new ApiTestClient(mvc).fromIp("10.2.0.2").login(username, PASSWORD);

        assertThat(status(result)).isEqualTo(200);
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        String ip = "10.3.0.1";
        String username = registerUser("10.3.0.99");
        ApiTestClient client = new ApiTestClient(mvc).fromIp(ip);
        failLogins(client, username, 4);

        assertThat(status(client.login(username, PASSWORD))).isEqualTo(200);
        failLogins(client, username, 4);

        assertThat(status(client.login(username, PASSWORD))).isEqualTo(200);
    }

    @Test
    void ipWithFiftyFailuresAcrossUsersGets429ForAnyLogin() {
        String ip = "10.4.0.1";
        String realUser = registerUser("10.4.0.99");
        ApiTestClient client = new ApiTestClient(mvc).fromIp(ip);
        for (int i = 0; i < 50; i++) {
            assertThat(status(client.login("fantasma" + i, "incorrecta1"))).isEqualTo(401);
        }

        MvcResult blocked = client.login(realUser, PASSWORD);

        assertThat(status(blocked)).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).matches("\\d+");
    }

    @Test
    void registrationNumberEleventhFromOneIpGets429WithRetryAfter() {
        String ip = "10.5.0.1";
        for (int i = 0; i < 10; i++) {
            registerUser(ip);
        }

        MvcResult blocked = new ApiTestClient(mvc)
                .fromIp(ip)
                .register(ApiTestClient.uniqueUsername("thr"), "Usuario", PASSWORD);

        assertThat(status(blocked)).isEqualTo(429);
        assertThat(blocked.getResponse().getHeader("Retry-After")).matches("\\d+");
        assertThat(body(blocked)).contains("Demasiados registros");
    }
}
