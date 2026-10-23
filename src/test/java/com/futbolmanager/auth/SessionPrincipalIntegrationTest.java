package com.futbolmanager.auth;

import static com.futbolmanager.support.ApiTestClient.json;
import static com.futbolmanager.support.ApiTestClient.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
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
class SessionPrincipalIntegrationTest {

    private static final String PASSWORD = "secreto123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void registersAndLogsInWithMaximumLengthUsernameAndDisplayName() {
        String username = ApiTestClient.uniqueUsername("a".repeat(19));
        String displayName = "D".repeat(60);
        assertThat(username).hasSize(30);

        MvcResult registered = new ApiTestClient(mvc).register(username, displayName, PASSWORD);
        assertThat(status(registered)).isEqualTo(201);
        String userId = json(registered, "$.id");

        ApiTestClient client = new ApiTestClient(mvc);
        assertThat(status(client.login(username, PASSWORD))).isEqualTo(200);
        assertThat(status(client.get("/api/auth/me"))).isEqualTo(200);

        long sessionsWithShortPrincipal = jdbc.sql("select count(*) from spring_session where principal_name = :p")
                .param("p", userId)
                .query(Long.class)
                .single();
        assertThat(sessionsWithShortPrincipal).isEqualTo(2);
    }
}
