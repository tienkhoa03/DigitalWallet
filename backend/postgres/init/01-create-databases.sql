-- Creates the test database alongside the dev one provisioned via POSTGRES_DB.
-- Testcontainers spin up their own container in CI; this is only for local
-- ./mvnw quarkus:dev convenience.
SELECT 'CREATE DATABASE digitalwallet_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digitalwallet_test')\gexec
