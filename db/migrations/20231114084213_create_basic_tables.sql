-- migrate:up

-- POSTGRES

CREATE TABLE IF NOT EXISTS client (
  id SERIAL,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS provider (
  id SERIAL,
  name VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS users (
  email VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  client_id INT,
  provider_id INT,
  PRIMARY KEY (email),
  FOREIGN KEY (client_id) REFERENCES client(id),
  FOREIGN KEY (provider_id) REFERENCES provider(id)
);

-- migrate:down

DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS client;
DROP TABLE IF EXISTS provider;
