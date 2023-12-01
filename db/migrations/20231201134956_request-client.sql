-- migrate:up

ALTER TABLE requests
ADD COLUMN client_id INTEGER REFERENCES client(id) ON DELETE CASCADE;

-- migrate:down

ALTER TABLE requests
DROP COLUMN IF EXISTS client_id;
