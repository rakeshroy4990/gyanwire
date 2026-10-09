-- Flyway's history table is created outside numbered migrations, so V5 never locked it.
-- Enable RLS + revoke Data API roles so Supabase stops exposing it.
SELECT gyanwire_lock('gyanwire_flyway_schema_history');
