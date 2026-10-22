package com.futbolmanager;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Every integration test imports this: {@code @Import(TestcontainersConfiguration.class)}.
 *
 * <p>Starts one throwaway postgres:17 per cached Spring context. {@code @ServiceConnection} replaces the
 * datasource URL/username/password that application.yml reads from DB_URL/DB_USERNAME/DB_PASSWORD (the
 * Surefire config in pom.xml supplies unused placeholder values so those placeholders resolve). Flyway
 * then applies V1 and V2 to the container. Requires Docker to be running.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
