-- migrate:up

ALTER TABLE provider
ADD COLUMN category VARCHAR(255);

-- migrate:down

ALTER TABLE provider
DROP COLUMN IF EXISTS category;