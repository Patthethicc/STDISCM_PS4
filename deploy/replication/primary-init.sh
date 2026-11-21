#!/usr/bin/env bash
# Bonus 1, primary side. Mount into the PRIMARY's /docker-entrypoint-initdb.d/ (e.g. as 05-replication.sh).
# The official postgres image runs it once, when the data directory is first created.
# It lets one standby stream from this primary:
#   1. a login role that may open replication connections
#   2. a physical replication slot, so the primary keeps WAL until the standby has it
#   3. a pg_hba.conf rule for replication connections ("all" in the database column does NOT cover them)
#
# Env: REPLICATION_PASSWORD (required), REPLICATION_SLOT (required, e.g. grade_replica),
#      REPLICATION_USER (default replicator)
# No "set -u" here: the image may source this file instead of executing it.
set -eo pipefail

REPLICATION_USER="${REPLICATION_USER:-replicator}"
: "${REPLICATION_PASSWORD:?set REPLICATION_PASSWORD}"
: "${REPLICATION_SLOT:?set REPLICATION_SLOT, e.g. grade_replica}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE ROLE ${REPLICATION_USER} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
SELECT pg_create_physical_replication_slot('${REPLICATION_SLOT}');
SQL

# The server restarts after the init scripts, so this rule is live on first real start.
echo "host replication ${REPLICATION_USER} all scram-sha-256" >> "$PGDATA/pg_hba.conf"
echo "primary-init: role ${REPLICATION_USER} and slot ${REPLICATION_SLOT} ready for streaming replication"
