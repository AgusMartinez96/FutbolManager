package com.futbolmanager.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test-only HTTP client over MockMvc that behaves like the SPA: it sends a valid CSRF token on every
 * request (unless told not to), carries the FM_SESSION cookie between calls, and lets a test choose
 * the client IP (login throttling is keyed by IP).
 *
 * <p>One instance = one browser. Create a second instance for a second user.
 */
public final class ApiTestClient {

    public static final String SESSION_COOKIE = "FM_SESSION";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final MockMvc mvc;
    private Cookie session;
    private String remoteAddr = "127.0.0.1";

    public ApiTestClient(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Usernames must be unique per test run; prefix at most 19 characters (total stays within 30). */
    public static String uniqueUsername(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public ApiTestClient fromIp(String ip) {
        this.remoteAddr = ip;
        return this;
    }

    public Cookie sessionCookie() {
        return session;
    }

    public void useSession(Cookie cookie) {
        this.session = cookie;
    }

    public MvcResult register(String username, String displayName, String password) {
        return post(
                "/api/auth/register",
                Map.of("username", username, "displayName", displayName, "password", password));
    }

    public MvcResult login(String username, String password) {
        return post("/api/auth/login", Map.of("username", username, "password", password));
    }

    public MvcResult get(String path) {
        return perform(MockMvcRequestBuilders.get(path), true);
    }

    public MvcResult post(String path, Object body) {
        return perform(withJson(MockMvcRequestBuilders.post(path), body), true);
    }

    public MvcResult postWithoutCsrf(String path, Object body) {
        return perform(withJson(MockMvcRequestBuilders.post(path), body), false);
    }

    public MvcResult put(String path, Object body) {
        return perform(withJson(MockMvcRequestBuilders.put(path), body), true);
    }

    public MvcResult patch(String path, Object body) {
        return perform(withJson(MockMvcRequestBuilders.patch(path), body), true);
    }

    public MvcResult delete(String path) {
        return perform(MockMvcRequestBuilders.delete(path), true);
    }

    public static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    public static String body(MvcResult result) {
        try {
            return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read response body", e);
        }
    }

    /** Reads a value with a JsonPath expression, e.g. {@code json(result, "$.id")}. */
    public static <T> T json(MvcResult result, String jsonPath) {
        return JsonPath.read(body(result), jsonPath);
    }

    private static MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder request, Object body) {
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        }
        return request;
    }

    private MvcResult perform(MockHttpServletRequestBuilder request, boolean withCsrf) {
        String ip = remoteAddr;
        request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        if (withCsrf) {
            request.with(csrf());
        }
        if (session != null) {
            request.cookie(session);
        }
        try {
            MvcResult result = mvc.perform(request).andReturn();
            Cookie returned = result.getResponse().getCookie(SESSION_COOKIE);
            if (returned != null) {
                session = returned.getMaxAge() == 0 ? null : returned;
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("MockMvc request failed", e);
        }
    }
}
