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
import org.springframework.session.web.http.*;

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
    @Bean SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }
    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy loginStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean CookieSerializer cookieSerializer(@Value("${english-hero.session.secure:false}") boolean secure, Clock clock) {
        var serializer = new DefaultCookieSerializer() {
            @Override public void writeCookieValue(CookieValue value) {
                if (value.getCookieMaxAge() != 0) {
                    var session = value.getRequest().getSession(false);
                    boolean remember = session != null && Boolean.TRUE.equals(session.getAttribute(IdentitySessionFilter.REMEMBER));
                    Long login = session == null ? null : (Long) session.getAttribute(IdentitySessionFilter.LOGIN_TIME);
                    value.setCookieMaxAge(remember && login != null ? (int) Math.max(0, Math.min(604800, 604800 - (clock.millis() - login) / 1000)) : -1);
                }
                super.writeCookieValue(value);
            }
        };
        serializer.setCookieName("EH_SESSION"); serializer.setCookiePath("/"); serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax"); serializer.setUseSecureCookie(secure);
        return serializer;
    }
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf,
            IdentityService identities, Clock clock,
            @Value("${springdoc.api-docs.enabled:false}") boolean docsEnabled) throws Exception {
        http.authorizeHttpRequests(access -> {
            access.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/system/status", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll();
            if (docsEnabled) {
                access.requestMatchers(HttpMethod.GET,
                    "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
            }
            access.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/auth/me", "/api/v1/auth/logout", "/api/v1/account/**").authenticated()
                .anyRequest().denyAll();
        });
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true));
        http.csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler()));
        http.addFilterAfter(new IdentitySessionFilter(identities, clock), SecurityContextHolderFilter.class);
        http.exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, error) -> IdentityErrors.write(response, request, 401, "UNAUTHENTICATED", "请先登录"))
            .accessDeniedHandler((request, response, error) -> IdentityErrors.write(response, request, 403,
                error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN", error instanceof CsrfException ? "页面凭据已失效，请重试" : "没有访问权限")));
        http.logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
