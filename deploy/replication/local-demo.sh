#!/usr/bin/env bash
# Bonus 1 on ONE machine, no Docker and no VMs: a grade_db primary on port 55432 and a
# streaming replica on port 55433, both under deploy/replication/.local/ (git-ignored).
# Use it to practise the failover demo before the VMs exist.
#
# Needs the PostgreSQL server tools (initdb, pg_ctl, pg_basebackup, psql) on PATH, or set PG_BIN,
# e.g. PG_BIN="/c/Program Files/PostgreSQL/17/bin" in Git Bash. Do not run as root/Administrator.
#
#   deploy/replication/local-demo.sh up             create + start primary and replica, load grade schema/seed
#   deploy/replication/local-demo.sh status         who is primary, is the replica streaming, row counts
#   deploy/replication/local-demo.sh stop-primary   simulate the primary database going down
#   deploy/replication/local-demo.sh start-primary  bring it back (the replica reconnects and catches up)
#   deploy/replication/local-demo.sh promote        make the replica the new primary (see README: split brain)
#   deploy/replication/local-demo.sh down           stop both and delete the demo data
#
# Then run the grade service against them:
#   GRADE_DB_PORT=55432 GRADE_DB_REPLICA_HOST=localhost GRADE_DB_REPLICA_PORT=55433 \
#     java -jar grade-service/target/grade-service-0.1.0-SNAPSHOT.jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
[ -n "${PG_BIN:-}" ] && PATH="$PG_BIN:$PATH"
DIR="${DEMO_DIR:-$ROOT/deploy/replication/.local}"
P="$DIR/primary" R="$DIR/replica" PP=55432 RP=55433
SUPER=postgres SUPER_PW=postgres REPL_PW=replpass SLOT=grade_replica

q() { PGPASSWORD=$SUPER_PW psql -X -q -h localhost -U $SUPER -v ON_ERROR_STOP=1 "$@"; }

up() {
  [ -e "$P" ] && { echo "already set up in $DIR (run 'down' first)"; exit 1; }
  mkdir -p "$DIR"; printf '%s\n' "$SUPER_PW" > "$DIR/pw"

  initdb -D "$P" -U $SUPER --pwfile="$DIR/pw" --auth-local=scram-sha-256 --auth-host=scram-sha-256 >/dev/null
  cat >> "$P/postgresql.conf" <<CONF
port = $PP
listen_addresses = 'localhost'
unix_socket_directories = ''
wal_level = replica
max_wal_senders = 10
hot_standby = on
CONF
  echo "host replication replicator all scram-sha-256" >> "$P/pg_hba.conf"
  pg_ctl -D "$P" -l "$DIR/primary.log" -w start >/dev/null

  q -p $PP -d postgres -c "CREATE ROLE grade LOGIN PASSWORD 'grade'" -c "CREATE DATABASE grade_db OWNER grade" \
       -c "CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '$REPL_PW'" \
       -c "SELECT pg_create_physical_replication_slot('$SLOT')" >/dev/null
  PGPASSWORD=grade psql -X -q -h localhost -p $PP -U grade -d grade_db -v ON_ERROR_STOP=1 \
       -f "$ROOT/db/grade/schema.sql" -f "$ROOT/db/grade/seed.sql"

  # The replica: a byte-for-byte copy of the primary, plus standby.signal + primary_conninfo (-R)
  PGPASSWORD=$REPL_PW pg_basebackup -h localhost -p $PP -U replicator -D "$R" -Fp -Xs -R -S $SLOT --checkpoint=fast
  sed -i.bak "s/^port = $PP/port = $RP/" "$R/postgresql.conf" && rm -f "$R/postgresql.conf.bak"
  PGPASSWORD=$REPL_PW pg_ctl -D "$R" -l "$DIR/replica.log" -w start >/dev/null
  sleep 1
  status
}

status() {
  for port in $PP $RP; do
    if q -p $port -d grade_db -tAc "SELECT 1" >/dev/null 2>&1; then
      role=$(q -p $port -d grade_db -tAc "SELECT CASE WHEN pg_is_in_recovery() THEN 'replica (read-only)' ELSE 'primary (read-write)' END")
      n=$(q -p $port -d grade_db -tAc "SELECT count(*) FROM grades")
      echo "port $port: $role, $n grades"
    else
      echo "port $port: DOWN"
    fi
  done
  q -p $PP -d postgres -tAc "SELECT 'streaming to replica: ' || state || ', lag ' || coalesce(replay_lag::text,'0') FROM pg_stat_replication" 2>/dev/null || true
}

case "${1:-}" in
  up) up ;;
  status) status ;;
  stop-primary) pg_ctl -D "$P" -m fast -w stop >/dev/null; echo "primary stopped"; status ;;
  start-primary) pg_ctl -D "$P" -l "$DIR/primary.log" -w start >/dev/null; sleep 1; echo "primary started"; status ;;
  promote) q -p $RP -d postgres -tAc "SELECT pg_promote()" >/dev/null; sleep 1; echo "replica promoted"; status ;;
  down)
    for d in "$R" "$P"; do [ -d "$d" ] && pg_ctl -D "$d" -m fast -w stop >/dev/null 2>&1 || true; done
    rm -rf "$DIR"; echo "removed $DIR" ;;
  *) sed -n '2,20p' "$0"; exit 1 ;;
esac
