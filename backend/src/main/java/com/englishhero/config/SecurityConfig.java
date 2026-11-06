package com.englishhero.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import com.englishhero.identity.*;
import java.time.Clock;
import java.util.List;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.authentication.session.*;


@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
    @Bean Clock clock() { return Clock.systemUTC(); }
    public static class AuthUser extends User {
        public final IdentityUser identity;
        public AuthUser(IdentityUser user, List<String> roles) {
            super(user.username(), user.passwordHash(), user.enabled(), true, true, true,
                roles.stream().map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList());
            identity = user;
        }
    }
    @Bean UserDetailsService userDetailsService(IdentityMapper mapper) {
        return name -> {
            var user = mapper.byUsername(name);
            if (user == null) throw new UsernameNotFoundException("Account unavailable");
            return new AuthUser(user, mapper.roles(user.id()));
        };
    }
    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users); provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean CookieCsrfTokenRepository csrfRepository(com.englishhero.identity.token.AuthProperties props) {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieName("EH_CSRF"); csrf.setCookiePath("/api/v1"); csrf.setHeaderName("X-CSRF-TOKEN");
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(props.isSecure()).sameSite("Lax"));
        return csrf;
    }
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrf, com.englishhero.identity.token.TokenAuthenticationService tokens, com.englishhero.identity.token.AuthProperties props,
            IdentityService identities, Clock clock,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) throws Exception {
        http.authorizeHttpRequests(access -> {
            access.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/system/status", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout", "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/email-codes", "/api/v1/auth/password-reset").permitAll();
            if (docsEnabled) {
                access.requestMatchers(HttpMethod.GET,
                    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
            }
            access.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,"/api/v1/wall/messages","/api/v1/wall/messages/*","/api/v1/wall/messages/*/comments").permitAll()
                .requestMatchers("/api/v1/wall/**").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/practice/catalog","/api/v1/practice/sets","/api/v1/practice/facets","/api/v1/practice/sets/*","/api/v1/practice/papers","/api/v1/practice/papers/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/practice/audio/*").permitAll()
                .requestMatchers(HttpMethod.HEAD,"/api/v1/practice/audio/*").permitAll()
                .requestMatchers("/api/v1/practice/**").authenticated()
                .requestMatchers("/api/v1/reading/selection-translations/**").authenticated()
                .requestMatchers("/api/v1/reading/checkins/week","/api/v1/reading/articles/*/versions/*/checkin","/api/v1/reading/attempts/**","/api/v1/reading/quizzes/*/attempts","/api/v1/reading/articles/*/versions/*/practice","/api/v1/reading/articles/*/versions/*/practice/history").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/dictionary/lookup","/api/v1/dictionary/phrase","/api/v1/dictionary/search").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/recommendation","/api/v1/reading/categories","/api/v1/reading/articles","/api/v1/reading/articles/*","/api/v1/reading/articles/*/versions/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/articles/*/versions/*/tools","/api/v1/reading/articles/*/versions/*/quiz").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/reading/articles/*/versions/*/quiz/guest-sessions","/api/v1/reading/quiz-guest/submit").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/articles/*/versions/*/writing-materials").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/articles/*/versions/*/words").authenticated()
                .requestMatchers("/api/v1/reading/favorites","/api/v1/reading/favorites/states","/api/v1/reading/articles/*/favorite").authenticated()
                .requestMatchers("/api/v1/vocabulary/words","/api/v1/vocabulary/words/*","/api/v1/vocabulary/learning/**","/api/v1/vocabulary/phrases","/api/v1/vocabulary/phrases/**").authenticated()
                .requestMatchers("/api/v1/writing-materials/*/personal","/api/v1/writing-materials/*/favorite","/api/v1/writing-materials/*/imitation").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/articles/*/versions/*/writing-materials/states").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/reading/categories","/api/v1/reading/articles","/api/v1/reading/articles/*/versions/*","/api/v1/reading/history").authenticated()
                .requestMatchers(HttpMethod.PUT,"/api/v1/reading/progress/*").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/reading/articles/*/start").authenticated()
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout", "/api/v1/account/**").authenticated()
                .anyRequest().denyAll();
        });
        http.sessionManagement(config -> config.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()));
        http.securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()));
        http.csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
            .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()));
        http.addFilterBefore(new com.englishhero.identity.token.AuthOriginFilter(props), CsrfFilter.class);
        http.addFilterAfter(new com.englishhero.identity.token.JwtIdentityFilter(tokens), SecurityContextHolderFilter.class);
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, error) -> IdentityErrors.write(response, request, 401, "UNAUTHENTICATED", "请先登录"))
            .accessDeniedHandler((request, response, error) -> IdentityErrors.write(response, request, 403,
                error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN", error instanceof CsrfException ? "页面凭据已失效，请重试" : "没有访问权限")));
        http.logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
