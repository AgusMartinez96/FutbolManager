package com.futbolmanager;

import java.time.Clock;
import java.util.List;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// El login es propio (AuthController + sesión): sin usuario en memoria ni contraseña generada.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaRepositories(considerNestedRepositories = true)
public class FutbolManagerApplication {

    private static final List<String> REQUIRED_ENV = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD");

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    public static void main(String[] args) {
        // Timestamps are UTC; also avoids legacy JVM zone ids (e.g. America/Buenos_Aires) that PostgreSQL rejects.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication app = new SpringApplication(FutbolManagerApplication.class);
        // Runs after spring.config.import has loaded .env, so it sees every property source.
        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            for (String name : REQUIRED_ENV) {
                String value = event.getEnvironment().getProperty(name);
                if (value == null || value.isBlank()) {
                    throw new IllegalStateException("Falta la variable de entorno obligatoria " + name);
                }
            }
        });
        app.run(args);
    }
}
