package com.futbolmanager.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Talks to a real embedded server (not MockMvc) so servlet-container behavior such as Tomcat's
 * RemoteIpValve is exercised, and lets a test claim a client address through X-Forwarded-For.
 */
public final class ForwardedLoginClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;

    public ForwardedLoginClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    /** Status of a login attempt for {@code username} that claims to come from {@code forwardedFor}. */
    public int loginStatus(String forwardedFor, String username, String password) {
        try {
            String csrf = csrfCookieValue();
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", forwardedFor)
                    .header("X-XSRF-TOKEN", csrf)
                    .header("Cookie", "XSRF-TOKEN=" + csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            throw new IllegalStateException("Login request failed", e);
        }
    }

    private String csrfCookieValue() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/csrf")).GET().build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        return response.headers().allValues("Set-Cookie").stream()
                .filter(c -> c.startsWith("XSRF-TOKEN="))
                .map(c -> c.substring("XSRF-TOKEN=".length(), c.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No XSRF-TOKEN cookie"));
    }
}
