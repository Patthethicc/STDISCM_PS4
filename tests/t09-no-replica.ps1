# Test 9: Jomi's compose file alone (no replica override): behaves like before. WIPES ALL DATA, then restores the normal stack.
. "$PSScriptRoot/lib.ps1"
Write-Host "== T9 regression without replicas"
try {
    dc down -v --remove-orphans | Out-Host
    docker compose --project-directory $global:Deploy -f "$global:Deploy/docker-compose.yml" --profile all up -d --build | Out-Host
    Check 'stack is up without replicas' (Wait-Stack 300 30)
    JavaCheck 'functional (16 checks)' @('tests/e2e/FailureTests.java', 'functional', 'http://localhost')
    Check 'no replica pool when no replica is configured' (-not ((Logs 'grade-service') -match 'grade-pool-replica'))
} finally {
    Write-Host '-- restoring the normal stack (with replicas)'
    dc down -v --remove-orphans | Out-Host
    dc up -d --build | Out-Host
    Wait-Stack 300 30 | Out-Null
}
Finish
