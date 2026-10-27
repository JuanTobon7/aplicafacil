#!/bin/bash
# Se ejecuta UNA sola vez, cuando el volumen de Postgres está vacío
# (para aplicar cambios: docker compose down -v && docker compose up -d).
# Un schema + un usuario por microservicio: ningún servicio puede leer
# las tablas de otro (cada uno es dueño de sus datos).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- pgvector (embeddings de profile-service)
    CREATE EXTENSION IF NOT EXISTS vector;

    CREATE USER identity_svc WITH PASSWORD '${IDENTITY_DB_PASSWORD}';
    CREATE SCHEMA identity AUTHORIZATION identity_svc;
    ALTER ROLE identity_svc SET search_path = identity, public;

    CREATE USER linkedin_svc WITH PASSWORD '${LINKEDIN_DB_PASSWORD}';
    CREATE SCHEMA linkedin AUTHORIZATION linkedin_svc;
    ALTER ROLE linkedin_svc SET search_path = linkedin, public;

    CREATE USER computrabajo_svc WITH PASSWORD '${COMPUTRABAJO_DB_PASSWORD}';
    CREATE SCHEMA computrabajo AUTHORIZATION computrabajo_svc;
    ALTER ROLE computrabajo_svc SET search_path = computrabajo, public;

    CREATE USER profile_svc WITH PASSWORD '${PROFILE_DB_PASSWORD}';
    CREATE SCHEMA profile AUTHORIZATION profile_svc;
    ALTER ROLE profile_svc SET search_path = profile, public;

    CREATE USER jobs_svc WITH PASSWORD '${JOBS_DB_PASSWORD}';
    CREATE SCHEMA jobs AUTHORIZATION jobs_svc;
    ALTER ROLE jobs_svc SET search_path = jobs, public;

    CREATE USER aiconfig_svc WITH PASSWORD '${AICONFIG_DB_PASSWORD}';
    CREATE SCHEMA aiconfig AUTHORIZATION aiconfig_svc;
    ALTER ROLE aiconfig_svc SET search_path = aiconfig, public;
EOSQL
