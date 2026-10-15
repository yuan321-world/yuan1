package com.englishhero.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicit local one-shot bootstrap. No HTTP entry and no role/password overwrite. */
@Component
@Profile("local")
@ConditionalOnProperty(name = "english-hero.bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final IdentityService identities;
    private final ConfigurableApplicationContext context;
    private final String username, password, displayName;
    public AdminBootstrap(IdentityService identities, ConfigurableApplicationContext context,
            @Value("${english-hero.bootstrap.username:}") String username,
            @Value("${english-hero.bootstrap.password:}") String password,
            @Value("${english-hero.bootstrap.display-name:管理员}") String displayName) {
        this.identities = identities; this.context = context; this.username = username; this.password = password; this.displayName = displayName;
    }
    @Override public void run(ApplicationArguments arguments) {
        identities.bootstrap(username, password, displayName);
        org.slf4j.LoggerFactory.getLogger(AdminBootstrap.class).info("admin_bootstrap_completed; stopping one-shot application");
        context.close();
    }
}
