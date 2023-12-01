-- migrate:up

ALTER TABLE users ADD COLUMN address VARCHAR(255) NOT NULL;

-- migrate:down

ALTER TABLE users DROP COLUMN address;

