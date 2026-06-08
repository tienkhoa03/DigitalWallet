-- Bootstraps a parallel test database alongside the dev database.
-- Runs once on first container start via docker-entrypoint-initdb.d.
-- Schema itself is owned by Flyway (backend_coding.md §13) — this only
-- creates the empty database so the `test` profile has somewhere to migrate.
-- POSTGRES_USER already exists; reuse it as owner.
SELECT 'CREATE DATABASE digitalwallet_test OWNER ' || quote_ident(current_user)
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'digitalwallet_test')
\gexec
