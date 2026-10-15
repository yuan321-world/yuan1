package com.englishhero.system;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaMigrationIT {
    @Test void migratesOnlyDedicatedTestDatabaseAndIsIdempotent() throws Exception {
        var file = new FileSystemResource("./config/application-test.yml");
        if (!file.exists()) {
            throw new IllegalStateException("Create config/application-test.yml from its example before mysql-it. Never use the dev database.");
        }
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(file);
        var properties = Objects.requireNonNull(yaml.getObject());
        var url = properties.getProperty("spring.datasource.url", "");
        // Guard before opening ANY connection or running ANY migration.
        assertThat(url).matches("^jdbc:mysql://[^/]+/english_hero_test(?:\\?.*)?$");
        var username = properties.getProperty("spring.datasource.username");
        var password = properties.getProperty("spring.datasource.password");
        assertThat(username).isNotBlank();
        assertThat(password).isNotBlank().isNotEqualTo("REPLACE_LOCALLY");

        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT VERSION(), DATABASE()")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(2)).isEqualTo("english_hero_test");
                System.out.println("MySQL server: " + result.getString(1) + ", database: " + result.getString(2));
            }
        }
        var flyway = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").cleanDisabled(true).load();
        flyway.migrate();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT metadata_value FROM system_metadata WHERE metadata_key='service_name'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("english-hero");
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE version='1' AND success=1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
            var tables = new ArrayList<String>();
            try (var result = statement.executeQuery("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
                while (result.next()) tables.add(result.getString(1).toLowerCase(java.util.Locale.ROOT));
            }
            assertThat(tables).containsExactlyInAnyOrder("system_metadata", "flyway_schema_history", "identity_user", "identity_user_role", "spring_session", "spring_session_attributes");
        }
    }
}
