package com.englishhero.system;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PhaseZeroStartupIT {
    @Autowired ApplicationContext context;
    @Autowired SystemStatusService status;

    @DynamicPropertySource
    static void dedicatedTestDatabaseOnly(DynamicPropertyRegistry registry) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource("./config/application-test.yml"));
        var properties = Objects.requireNonNull(yaml.getObject());
        var url = properties.getProperty("spring.datasource.url", "");
        // Fail before creating the full application context if the database is not the test schema.
        assertThat(url).matches("^jdbc:mysql://[^/]+/english_hero_test(?:\\?.*)?$");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> properties.getProperty("spring.datasource.username"));
        registry.add("spring.datasource.password", () -> properties.getProperty("spring.datasource.password"));
    }

    @Test void fullApplicationUsesRealMetadataWithoutCreatingAnAutomaticIdentity() {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(status.getStatus()).isEqualTo(new SystemStatusResponse("UP", "english-hero", "UP"));
    }
}
