package com.gyanwire.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class FlywayRepairOnMigrateConfigTest {

    @Test
    void repairsBeforeMigrate() {
        FlywayMigrationStrategy strategy = new FlywayRepairOnMigrateConfig().flywayMigrationStrategy();
        Flyway flyway = mock(Flyway.class);

        strategy.migrate(flyway);

        var inOrder = inOrder(flyway);
        inOrder.verify(flyway).repair();
        inOrder.verify(flyway).migrate();
        verifyNoMoreInteractions(flyway);
    }
}
