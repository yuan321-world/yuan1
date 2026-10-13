package com.englishhero.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) throws Exception {
        http.authorizeHttpRequests(access -> {
            access.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/system/status").permitAll();
            if (docsEnabled) {
                access.requestMatchers(HttpMethod.GET,
                    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
            }
            access.anyRequest().denyAll();
        });
        // CSRF remains enabled. Real identity/session behavior arrives in phase 1.
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
