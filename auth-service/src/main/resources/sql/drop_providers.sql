-- Phase 0: Drop OAuth2 Provider tables (no longer needed)
-- Run this migration on any environment that previously had the providers table

-- Drop join table first (FK constraint)
DROP TABLE IF EXISTS provider_user;

-- Drop providers table
DROP TABLE IF EXISTS providers;
