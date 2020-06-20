-- v6.0.0-alpha1 init --
BEGIN;

DROP TABLE IF EXISTS core_config;
CREATE TABLE core_config(
	guild_id BIGINT PRIMARY KEY,
	prefix VARCHAR(64),
	channel_changelog_id BIGINT
);

DROP TABLE IF EXISTS bot_admin;
CREATE TABLE bot_admin(
	user_id BIGINT PRIMARY KEY
);

DROP TABLE IF EXISTS blacklisted_id;
CREATE TABLE blacklisted_id(
	id BIGINT PRIMARY KEY
);

COMMIT;