package com.futbolmanager.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    public record Health(String status, String version) {}

    private final String version;

    public HealthController(@Value("${fm.version}") String version) {
        this.version = version;
    }

    @GetMapping("/api/health")
    public Health health() {
        return new Health("UP", version);
    }
}
