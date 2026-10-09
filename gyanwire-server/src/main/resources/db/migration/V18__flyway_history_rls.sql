-- Do not ALTER gyanwire_flyway_schema_history here.
-- Flyway holds that table during migrate, so gyanwire_lock() deadlocks.
-- Lockdown runs after Flyway via FlywayHistoryRlsLockdown.
SELECT 1;
