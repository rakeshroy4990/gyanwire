package com.gyanwire.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Locks Flyway's history table against Supabase Data API roles after migrate.
 * Cannot run inside a Flyway migration: Flyway already holds that table.
 */
@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
public class FlywayHistoryRlsLockdown implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FlywayHistoryRlsLockdown.class);
    private static final String TABLE = "gyanwire_flyway_schema_history";

    private final DataSource dataSource;

    public FlywayHistoryRlsLockdown(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT gyanwire_lock('" + TABLE + "')");
            log.info("RLS lockdown applied to {}", TABLE);
        } catch (Exception e) {
            log.warn("Could not lock {}: {}", TABLE, e.getMessage());
        }
    }
}
