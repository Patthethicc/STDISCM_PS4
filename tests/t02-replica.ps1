# Test 2: every replica is a read-only standby and every primary streams to it.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T2 replication"
foreach ($n in 'auth', 'course', 'grade', 'feature') {
    $db = "${n}_db"
    Check "$n replica is a standby (pg_is_in_recovery = t)" ((Psql "$n-db-replica" $n $db 'select pg_is_in_recovery()') -eq 't')
    Check "$n primary is streaming to its replica" ((Psql "$n-db" $n $db "select count(*) from pg_stat_replication where state='streaming'") -eq '1')
    Check "$n replica refuses writes (read-only)" ((Psql "$n-db-replica" $n $db 'create table zz_probe(i int)') -match 'read-only')
}
Finish
