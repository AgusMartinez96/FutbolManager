package com.futbolmanager.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import com.futbolmanager.support.ForwardedLoginClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** With the prod profile the proxy in front of the app is trusted: the throttle sees the real client address. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdClientIpIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void throttlesPerRealClientAddressBehindTheProxy() {
        ForwardedLoginClient client = new ForwardedLoginClient(port);
        String username = ApiTestClient.uniqueUsername("prd");
        for (int i = 0; i < 5; i++) {
            assertThat(client.loginStatus("203.0.113.7", username, "incorrecta1")).isEqualTo(401);
        }

        assertThat(client.loginStatus("203.0.113.7", username, "incorrecta1")).isEqualTo(429);
        assertThat(client.loginStatus("198.51.100.99", username, "incorrecta1")).isEqualTo(401);
    }
}
