package com.futbolmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import com.futbolmanager.support.ForwardedLoginClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/** Without the prod profile nobody is a trusted proxy: X-Forwarded-For must not choose the throttling IP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LocalClientIpIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void ignoresXForwardedForSoSpoofingTheHeaderDoesNotEvadeTheThrottle() {
        ForwardedLoginClient client = new ForwardedLoginClient(port);
        String username = ApiTestClient.uniqueUsername("loc");
        for (int i = 0; i < 5; i++) {
            assertThat(client.loginStatus("203.0.113." + i, username, "incorrecta1"))
                    .isEqualTo(401);
        }

        assertThat(client.loginStatus("198.51.100.99", username, "incorrecta1")).isEqualTo(429);
    }
}
