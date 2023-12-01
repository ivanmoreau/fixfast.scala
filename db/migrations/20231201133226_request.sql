-- migrate:up

CREATE TABLE requests (
    id SERIAL PRIMARY KEY,
    service_description TEXT NOT NULL,
    request_date VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    provider_id INTEGER REFERENCES provider(id) ON DELETE CASCADE
);


-- migrate:down

DROP TABLE IF EXISTS requests;
