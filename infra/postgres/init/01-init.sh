#!/bin/bash
# Se ejecuta UNA sola vez, cuando el volumen de Postgres está vacío.
# Un schema + un usuario por microservicio: ningún servicio puede leer
# las tablas de otro (cada uno es dueño de sus datos).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- pgvector (embeddings de profile-service)
    CREATE EXTENSION IF NOT EXISTS vector;

    CREATE USER people_svc WITH PASSWORD '${PEOPLE_DB_PASSWORD}';
    CREATE SCHEMA people AUTHORIZATION people_svc;
    ALTER ROLE people_svc SET search_path = people, public;

    CREATE USER profile_svc WITH PASSWORD '${PROFILE_DB_PASSWORD}';
    CREATE SCHEMA profile AUTHORIZATION profile_svc;
    ALTER ROLE profile_svc SET search_path = profile, public;

    CREATE USER jobs_svc WITH PASSWORD '${JOBS_DB_PASSWORD}';
    CREATE SCHEMA jobs AUTHORIZATION jobs_svc;
    ALTER ROLE jobs_svc SET search_path = jobs, public;

    CREATE USER auth_svc WITH PASSWORD '${AUTH_DB_PASSWORD}';
    CREATE SCHEMA auth AUTHORIZATION auth_svc;
    ALTER ROLE auth_svc SET search_path = auth, public;
EOSQL
