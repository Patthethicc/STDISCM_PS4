#!/usr/bin/env bash
# Bonus 1, replica side. Use as the REPLICA container's entrypoint (run as user postgres).
# First start (empty data directory): copy the primary with pg_basebackup -R, which also writes
# standby.signal and primary_conninfo, so the server starts as a read-only hot standby and keeps
# streaming WAL from the primary. Later starts: just run the standby.
#
# Env: PRIMARY_HOST (required), PRIMARY_PORT (default 5432), REPLICATION_PASSWORD (required),
#      REPLICATION_SLOT (required, same name as on the primary), REPLICATION_USER (default replicator),
#      PGDATA (default /var/lib/postgresql/data, the postgres:17 image path)
set -euo pipefail

PGDATA="${PGDATA:-/var/lib/postgresql/data}"
PRIMARY_PORT="${PRIMARY_PORT:-5432}"
REPLICATION_USER="${REPLICATION_USER:-replicator}"
: "${PRIMARY_HOST:?set PRIMARY_HOST to the address of the primary database}"
: "${REPLICATION_PASSWORD:?set REPLICATION_PASSWORD}"
: "${REPLICATION_SLOT:?set REPLICATION_SLOT}"
export PGPASSWORD="$REPLICATION_PASSWORD"   # also used by the WAL receiver when it reconnects

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "replica: empty data directory, copying ${PRIMARY_HOST}:${PRIMARY_PORT}"
  until pg_basebackup -h "$PRIMARY_HOST" -p "$PRIMARY_PORT" -U "$REPLICATION_USER" \
        -D "$PGDATA" -Fp -Xs -R -S "$REPLICATION_SLOT" --checkpoint=fast; do
    echo "replica: primary not reachable yet, retrying in 3 s"
    rm -rf "${PGDATA:?}"/*            # a failed copy leaves files behind; start clean
    sleep 3
  done
  chmod 700 "$PGDATA"
  echo "replica: base backup done, starting as hot standby"
fi

exec postgres -D "$PGDATA"
