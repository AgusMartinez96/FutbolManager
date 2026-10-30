package com.futbolmanager.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.futbolmanager.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SecurityDefaultsIntegrationTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    FilterChainProxy filterChainProxy;

    @Test
    void hasNoDefaultInMemoryUser() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(context.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
        assertThat(context.getBeansOfType(AuthenticationProvider.class)).isEmpty();
    }

    @Test
    void hasNeitherHttpBasicNorFormLogin() {
        List<DefaultSecurityFilterChain> chains = filterChainProxy.getFilterChains().stream()
                .map(DefaultSecurityFilterChain.class::cast)
                .toList();

        assertThat(chains).isNotEmpty();
        for (DefaultSecurityFilterChain chain : chains) {
            assertThat(chain.getFilters())
                    .noneMatch(BasicAuthenticationFilter.class::isInstance)
                    .noneMatch(UsernamePasswordAuthenticationFilter.class::isInstance);
        }
    }
}
