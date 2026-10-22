package com.futbolmanager.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import com.futbolmanager.support.ApiTestClient;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayCreatesAllTables() {
        List<String> tables = List.of(
                "spring_session",
                "spring_session_attributes",
                "users",
                "groups",
                "memberships",
                "players",
                "matches",
                "match_call_ups",
                "match_lineup_entries",
                "match_events",
                "elo_changes");
        for (String table : tables) {
            String regclass = jdbc.sql("select to_regclass(:name)::text")
                    .param("name", "public." + table)
                    .query(String.class)
                    .single();
            assertThat(regclass).as("table %s", table).isNotNull();
        }
    }

    @Test
    void healthIsUpWithoutSessionCookie() {
        MvcResult result = new ApiTestClient(mvc).get("/api/health");

        assertThat(ApiTestClient.status(result)).isEqualTo(200);
        assertThat(ApiTestClient.<String>json(result, "$.status")).isEqualTo("UP");
        assertThat(ApiTestClient.<String>json(result, "$.version")).isEqualTo("dev");
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(c -> c.startsWith(ApiTestClient.SESSION_COOKIE));
    }

    @Test
    void protectedRouteWithoutSessionIs401ProblemJson() {
        MvcResult result = new ApiTestClient(mvc).get("/api/groups");

        assertThat(ApiTestClient.status(result)).isEqualTo(401);
        assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    }
}
