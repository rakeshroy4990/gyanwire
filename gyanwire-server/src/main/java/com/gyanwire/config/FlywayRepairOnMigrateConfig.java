package com.gyanwire.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot ignores {@code spring.flyway.repair-on-migrate}; this bean makes it real.
 * Used after intentional edits to already-applied migrations (e.g. V2 legacy users).
 */
@Configuration
@ConditionalOnProperty(name = "spring.flyway.repair-on-migrate", havingValue = "true")
public class FlywayRepairOnMigrateConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairOnMigrateConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("Repairing Flyway schema history before migrate");
            flyway.repair();
            flyway.migrate();
        };
    }
}
