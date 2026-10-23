package com.futbolmanager.config;

import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Spring Boot only applies {@code server.servlet.session.cookie.*} to Spring Session when an embedded web
 * server runs, so MockMvc tests would see the default {@code SESSION} cookie. Building the serializer from
 * the same properties keeps {@code application.yml} the single source of truth in every environment.
 */
@Configuration
public class SessionCookieConfig {

    @Bean
    CookieSerializer cookieSerializer(ServerProperties server) {
        Cookie cookie = server.getServlet().getSession().getCookie();
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        if (cookie.getName() != null) {
            serializer.setCookieName(cookie.getName());
        }
        if (cookie.getHttpOnly() != null) {
            serializer.setUseHttpOnlyCookie(cookie.getHttpOnly());
        }
        if (cookie.getSecure() != null) {
            serializer.setUseSecureCookie(cookie.getSecure());
        }
        if (cookie.getMaxAge() != null) {
            serializer.setCookieMaxAge((int) cookie.getMaxAge().toSeconds());
        }
        if (cookie.getSameSite() != null) {
            serializer.setSameSite(cookie.getSameSite().attributeValue());
        }
        return serializer;
    }
}
