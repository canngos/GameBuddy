#!/bin/sh
# Runs last on a FRESH database (99- sorts after the baseline and seed).
#
# Creates the least-privileged application login the backend connects as, so the app never
# uses the bootstrap superuser. A superuser database login is remote code execution
# (`COPY ... FROM PROGRAM` runs shell commands on the DB host) and can read arbitrary files;
# a plain DML role can do neither. The bootstrap role (POSTGRES_USER) stays superuser because
# Postgres requires it, and is used only for init and migrations. See SECURITY_REVIEW.md, C3.
set -e
: "${DB_APP_PASSWORD:?DB_APP_PASSWORD must be set (the least-privileged app login password)}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
DO \$\$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'gamebuddy_app') THEN
    CREATE ROLE gamebuddy_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
  END IF;
END \$\$;
ALTER ROLE gamebuddy_app PASSWORD '$DB_APP_PASSWORD';
ALTER ROLE gamebuddy_app SET search_path = gamebuddy, public;
GRANT USAGE ON SCHEMA gamebuddy TO gamebuddy_app;
GRANT ALL ON ALL TABLES IN SCHEMA gamebuddy TO gamebuddy_app;
GRANT ALL ON ALL SEQUENCES IN SCHEMA gamebuddy TO gamebuddy_app;
GRANT TEMPORARY ON DATABASE "$POSTGRES_DB" TO gamebuddy_app;
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA gamebuddy GRANT ALL ON TABLES TO gamebuddy_app;
ALTER DEFAULT PRIVILEGES FOR ROLE "$POSTGRES_USER" IN SCHEMA gamebuddy GRANT ALL ON SEQUENCES TO gamebuddy_app;
SQL

echo "gamebuddy_app least-privileged application role is ready."
