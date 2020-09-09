-- v6.0.0-alpha1 to v6.0.0-alpha3 migration --
BEGIN;

ALTER TABLE core_config DROP COLUMN channel_changelog_id;

COMMIT;